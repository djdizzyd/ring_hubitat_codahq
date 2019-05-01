/**
 *  Ring Generic Light Device Driver
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
  definition(name: "Ring Generic Light", namespace: "codahq-hubitat", author: "Ben Rimmasch") {
    capability "Actuator"
    capability "Switch"
    capability "Sensor"
    capability "Refresh"
    capability "Polling"
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
  logDebug "Attempting to switch on."
  parent.simpleRequest("light-on", [dni: device.deviceNetworkId])
}

def off() {
  logDebug "Attempting to switch off."
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
  logInfo "Device ${device.label} is ${json.led_status}"
  sendEvent(name: "switch", value: json.led_status)
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
