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

    command "alarmOff"
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
  parent.simpleRequest("refresh-device", [dni: device.deviceNetworkId])
}

def on() {
  state.strobing = false
  logDebug "Attempting to switch on."
  parent.simpleRequest("light-on", [dni: device.deviceNetworkId])
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
  parent.simpleRequest("light-off", [dni: device.deviceNetworkId])
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
    parent.simpleRequest("siren-off", [dni: device.deviceNetworkId])
  }
}

def siren() {
  logDebug "Attempting to turn on siren."
  parent.simpleRequest("siren-on", [dni: device.deviceNetworkId])
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
  parent.simpleRequest("light-on", [dni: device.deviceNetworkId])
}

def strobeOff() {
  if (!state.strobing) return
  runInMillis(strobeRate.toInteger(), strobeOn)
  parent.simpleRequest("light-off", [dni: device.deviceNetworkId])
}

def childParse(type, params) {
  logDebug "childParse(type, msg)"
  logTrace "type ${type}"
  logTrace "params ${params}"

  if (type == "refresh-device") {
    logTrace "refresh"
    handleRefresh(params.msg)
  }
  else if (type == "light-on" || type == "light-off") {
    logTrace "switch"
    handleSwitch(type, params.response)
  }
  else if (type == "siren-on" || type == "siren-off") {
    logTrace "siren"
    handleSiren(type, params.response, params.msg)
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
  logInfo "Switch for device ${device.label} is ${json.led_status}"
  sendEvent(name: "switch", value: json.led_status)
  def value = json.siren_status.seconds_remaining > 0 ? "siren" : "off"
  logInfo "Alarm for device ${device.label} is ${value}"
  sendEvent(name: "alarm", value: value)
  if (value == "siren") {
    runIn(json.siren_status.seconds_remaining + 1, refresh)
  }
}

private handleSwitch(id, result) {
  logTrace "handleSwitch(${id}, ${result})"
  if (result != 200) {
    log.warn "Not successful?"
    return
  }
  logInfo "Device ${device.label} is ${id.split("-")[1]}"
  sendEvent(name: "switch", value: id.split("-")[1])
}

private handleSiren(id, result, json) {
  logTrace "handleSiren(${id}, ${result}, json)"
  logTrace "json: ${json}"
  if (result != 200) {
    log.warn "Not successful?"
    return
  }
  def value = id == "siren-on" ? (device.currentValue("alarm") == "both" ? "both" : "siren") : "off"
  logInfo "Alarm for device ${device.label} is ${value}"
  if (id == "siren-on") {
    runIn(json.seconds_remaining + 1, refresh)
  }
  if (value != "both") {
    sendEvent(name: "alarm", value: value)
  }
}
