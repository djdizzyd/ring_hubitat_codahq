/**
 *  Ring Generic Light with Siren Device Driver
 *
 *  Copyright 2019 Ben Rimmasch
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *
 *  Change Log:
 *  2019-03-02: Initial
 *
 */

import groovy.json.JsonSlurper

metadata {
  definition(name: "Ring Generic Light with Siren", namespace: "codahq-hubitat", author: "Ben Rimmasch") {
    capability "Actuator"
    capability "Switch"
    capability "Sensor"
    capability "Refresh"
    capability "Polling"
    capability "Alarm"
    capability "MotionSensor"

    command "alarmOff"
    command "getDings"
  }

  // simulator metadata
  simulator {
  }

  // UI tile definitions
  tiles {
    standardTile("button", "device.switch", width: 2, height: 2, canChangeIcon: true) {
      state "off", label: 'Off', action: "switch.on", icon: "st.switches.switch.off", backgroundColor: "#ffffff", nextState: "on"
      state "on", label: 'On', action: "switch.off", icon: "st.switches.switch.on", backgroundColor: "#00A0DC", nextState: "off"
    }
    main "button"
    details "button"
  }
  preferences {
    input name: "lightPolling", type: "bool", title: "Enable polling for light status on this device", defaultValue: false
    input name: "lightInterval", type: "number", range: 10..600, title: "Number of seconds in between light polls", defaultValue: 15
    input name: "strobeTimeout", type: "enum", title: "Strobe Timeout", options: [[30: "30s"], [60: "1m"], [120: "2m"], [180: "3m"]], defaultValue: 30
    input name: "strobeRate", type: "enum", title: "Strobe rate", options: [[1000: "1s"], [2000: "2s"], [5000: "5s"]], defaultValue: 1000
    input name: "descriptionTextEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: false
    input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false
    input name: "traceLogEnable", type: "bool", title: "Enable trace logging", defaultValue: false
  }
}

private logInfo(msg) {
  if (descriptionTextEnable) log.info msg
}

def logDebug(msg) {
  if (logEnable) log.debug msg
}

def logTrace(msg) {
  if (traceLogEnable) log.trace msg
}

def configure() {

}

def parse(String description) {
  logDebug "description: ${description}"
}

def poll() {
  refresh()
}

def refresh() {
  logDebug "refresh()"
  parent.simpleRequest("refresh", [dni: device.deviceNetworkId])
}

def getDings() {
  logDebug "getDings()"
  parent.simpleRequest("dings")
}

def setupPolling() {
  unschedule()
  if (lightPolling) {
    pollLight()
  }
}

def pollLight() {
  logTrace "pollLight()"
  refresh()
  if (pollLight) {
    runIn(lightInterval, pollLight)  //time in seconds
  }
}

def updated() {
  setupPolling()
}

def on() {
  state.strobing = false
  logDebug "Attempting to switch on."
  parent.simpleRequest("device-set", [dni: device.deviceNetworkId, kind: "doorbots", action: "floodlight_light_on"])
}

def off(boolean modifyAlarm = true) {
  if (modifyAlarm) {
    alarmOff(false)
  }
  switchOff()
}

def switchOff() {
  if (state.strobing) {
    unschedule()
  }
  state.strobing = false
  logDebug "Attempting to set switch to off."
  parent.simpleRequest("device-set", [dni: device.deviceNetworkId, kind: "doorbots", action: "floodlight_light_off"])
}

def alarmOff(boolean modifyLight = true) {
  logDebug "Attempting to set alarm to off."
  def alarm = device.currentValue("alarm")
  logTrace "alarm: $alarm"
  sendEvent(name: "alarm", value: "off")
  if ((alarm == "strobe" || alarm == "both") && modifyLight) {
    switchOff()
  }
  if (alarm == "siren" || alarm == "both") {
    parent.simpleRequest("device-set", [dni: device.deviceNetworkId, kind: "doorbots", action: "siren_off"])
  }
}

def siren() {
  logDebug "Attempting to turn on siren."
  parent.simpleRequest("device-set", [dni: device.deviceNetworkId, kind: "doorbots", action: "siren_on"])
}

def strobe(value = "strobe") {
  logInfo "${device.getDisplayName()} was set to strobe with a rate of ${strobeRate} milliseconds for ${strobeTimeout.toInteger()} seconds"
  state.strobing = true
  strobeOn()
  sendEvent(name: "alarm", value: value)
  runIn(strobeTimeout.toInteger(), alarmOff)
}

def both() {
  logDebug "Attempting to turn on siren and strobe."
  strobe("both")
  siren()
}

def strobeOn() {
  if (!state.strobing) return
  runInMillis(strobeRate.toInteger(), strobeOff)
  parent.simpleRequest("device-set", [dni: device.deviceNetworkId, kind: "doorbots", action: "floodlight_light_on"])
}

def strobeOff() {
  if (!state.strobing) return
  runInMillis(strobeRate.toInteger(), strobeOn)
  parent.simpleRequest("device-set", [dni: device.deviceNetworkId, kind: "doorbots", action: "floodlight_light_off"])
}

def childParse(type, params) {
  logDebug "childParse(type, msg)"
  logTrace "type ${type}"
  logTrace "params ${params}"

  if (type == "refresh") {
    logTrace "refresh"
    handleRefresh(params.msg)
  }
  else if (type == "device-set") {
    logTrace "set"
    handleSet(type, params)
  }
  else if (type == "dings") {
    logTrace "dings"
    handleDings(params.msg)
  }
  else {
    log.error "Unhandled type ${type}"
  }
}

private handleRefresh(json) {
  logTrace "handleRefresh(${json.description})"
  if (!json.led_status) {
    log.warn "No status?"
    return
  }

  if (json.led_status) {
    checkChanged("switch", json.led_status)
  }

  if (json.siren_status?.seconds_remaining && json.siren_status.seconds_remaining > 0) {
    def value = json.siren_status.seconds_remaining > 0 ? "siren" : "off"
    checkChanged("alarm", value)
    if (value == "siren") {
      runIn(json.siren_status.seconds_remaining + 1, refresh)
    }
  }
}

private handleSet(id, params) {
  logTrace "handleSet(${id}, ${params})"
  if (params.response != 200) {
    log.warn "Not successful?"
    return
  }
  if (params.action == "floodlight_light_on") {
    logInfo "Device ${device.label} switch is on"
    sendEvent(name: "switch", value: "on")
  }
  else if (params.action == "floodlight_light_off") {
    logInfo "Device ${device.label} switch is off"
    sendEvent(name: "switch", value: "off")
  }
  else if (params.action == "siren_on") {
    def value = device.currentValue("alarm") == "both" ? "both" : "siren"
    if (value != "both") {
      logInfo "Device ${device.label} alarm is ${value}"
      sendEvent(name: "alarm", value: value)
    }
    runIn(params.msg.seconds_remaining + 1, refresh)
  }
  else if (params.action == "siren_off") {
    logInfo "Device ${device.label} alarm is off"
    sendEvent(name: "alarm", value: "off")
  }
  else {
    log.error "Unsupported set ${params.action}"
  }

}

private handleDings(json) {
  logTrace "json: ${json}"
  if (json == null) {
    checkChanged("motion", "inactive")
  }
  else if (json.kind == "motion" && json.motion == true) {
    checkChanged("motion", "active")
  }
}

def checkChanged(attribute, newStatus) {
  if (device.currentValue(attribute) != newStatus) {
    logInfo "${attribute.capitalize()} for device ${device.label} is ${newStatus}"
    sendEvent(name: attribute, value: newStatus)
  }
}