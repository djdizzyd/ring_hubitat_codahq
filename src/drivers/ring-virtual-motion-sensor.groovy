/**
 *  Ring Virtual Motion Sensor Driver
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
 *  2019-04-26: Initial
 *
 */

import groovy.json.JsonSlurper

metadata {
  definition(name: "Ring Virtual Motion Sensor", namespace: "codahq-hubitat", author: "Ben Rimmasch") {
    capability "Refresh"
    capability "Sensor"
    capability "Motion Sensor"
    capability "Battery"
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

def refresh() {
  logDebug "Attempting to refresh."
  //parent.simpleRequest("refresh-device", [dni: device.deviceNetworkId])
}

def setValues(params) {
  logDebug "setValues(params)"
  logTrace "params: ${params}"
  if (params.motion && device.currentValue("motion") != params.motion) {
    logInfo "Motion for device ${device.label} is ${params.motion}"
    sendEvent(name: "motion", value: params.motion)
  }
  if (params.battery) {
    sendEvent(name: "battery", value: params.battery)
  }
  if (params.lastUpdate) {
    state.lastUpdate = params.lastUpdate
  }
  if (params.impulseType) {
    state.impulseType = params.impulseType
  }
  if (params.lastCommTime) {
    state.signalStrength = params.lastCommTime
  }
  if (params.nextExpectedWakeup) {
    state.nextExpectedWakeup = params.nextExpectedWakeup
  }
  if (params.signalStrength) {
    state.signalStrength = params.signalStrength
  }
  if (params.firmware && device.getDataValue("firmware") != params.firmware) {
    device.updateDataValue("firmware", params.firmware)
  }
  if (params.hardwareVersion && device.getDataValue("hardwareVersion") != params.hardwareVersion) {
    device.updateDataValue("hardwareVersion", params.hardwareVersion)
  }
}
/*
def childParse(type, params = []) {
  logDebug "childParse(type, params)"
  logTrace "type ${type}"
  logTrace "params ${params}"

  if (type == "refresh-device") {
    logTrace "refresh"
    handleRefresh(params.msg)
  }
  else if (type == "chime-motion" || type == "chime-ding") {
    logTrace "beep"
    handleBeep(type, params.response)
  }
  else if (type == "chime-volume" || type == "chime-mute" || type == "chime-unmute") {
    logTrace "volume"
    handleVolume(type, params)
  }
  else {
    log.error "Unhandled type ${type}"
  }
}

private handleBeep(id, result) {
  logTrace "handleBeep(${id}, ${result})"
  if (result != 204) {
    log.warn "Not successful?"
    return
  }
  logInfo "Device ${device.label} played ${id.split("-")[1]}"
}

private handleVolume(id, params) {
  logTrace "handleVolume(${id}, ${params})"
  if (params.response != 204) {
    log.warn "Not successful?"
    return
  }
  if (id == "chime-mute") {
    sendEvent(name: "mute", value: "muted")
    logInfo "Device ${device.label} set to muted"
  }
  else if (id == "chime-unmute") {
    sendEvent(name: "mute", value: "unmuted")
    logInfo "Device ${device.label} set to unmuted"
  }
  else {
    sendEvent(name: "volume", value: (params.volume as Integer) * 10)
    logInfo "Device ${device.label} volume set to ${params.volume}"
  }
}

private handleRefresh(json) {
  logDebug "handleRefresh(json)"
  logTrace "json ${json}"
  if (!json.settings) {
    log.warn "No volume?"
    return
  }
  if (device.currentValue("volume") != (json.settings.volume as Integer) * 10) {
    sendEvent(name: "volume", value: (json.settings.volume as Integer) * 10)
    logInfo "Device ${device.label} volume set to ${(json.settings.volume as Integer) * 10}"
  }
  if (device.currentValue("mute") == null) {
    sendEvent(name: "mute", value: "unmuted")
    logInfo "Device ${device.label} set to unmuted"
  }
  if (state.prevVolume == null) {
    state.prevVolume = 50
    logInfo "No previous volume found so arbitrary value given"
  }
}
*/