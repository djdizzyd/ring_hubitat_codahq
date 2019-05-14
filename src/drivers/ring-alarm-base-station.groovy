/**
 *  Ring Alarm Base Station Driver
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
  definition(name: "Ring Alarm Base Station", namespace: "codahq-hubitat", author: "Ben Rimmasch") {
    capability "Actuator"

    attribute "mode", "string"

    command "setMode", [[name: "Set Mode*", type: "ENUM", description: "Set the alarm's mode", constraints: ["Disarmed", "Home", "Away"]]]
  }

  preferences {
    input name: "syncRingToHsm", type: "bool", title: "Sync Ring Alarm mode to HSM mode?", defaultValue: false
    input name: "cancelAlertsOnDisarm", type: "bool", title: "Cancel HSM Alerts on Ring Alarm Disarm?", defaultValue: true
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

def setMode(mode) {
  logDebug "setMode(${mode})"
  if (mode == "Disarmed" && device.currentValue("mode") != "off") {
    parent.simpleRequest("setmode", [mode: "none", zid: device.getDataValue("zid"), dst: device.getDataValue("dst")])
  }
  else if (mode == "Home" && device.currentValue("mode") != "home") {
    parent.simpleRequest("setmode", [mode: "some", zid: device.getDataValue("zid"), dst: device.getDataValue("dst")])
  }
  else if (mode == "Away" && device.currentValue("mode") != "away") {
    parent.simpleRequest("setmode", [mode: "all", zid: device.getDataValue("zid"), dst: device.getDataValue("dst")])
  }
  else {
    logInfo "${device.label} already set to ${mode}.  No change necessary"
  }
}

def refresh() {
  logDebug "Attempting to refresh."
  parent.simpleRequest("refresh", [dst: device.deviceNetworkId])
}

private getRING_TO_HSM_MODE_MAP() {
  return [
    "home": "armHome",
    "away": "armAway",
    "off": "disarm"
  ]
}

def setValues(params) {
  if (params.mode && device.currentValue("mode") != params.mode) {
    logInfo "Alarm mode for device ${device.label} is ${params.mode}"
    sendEvent(name: "mode", value: params.mode)
  }
  if (syncRingToHsm && location.hsmStatus != RING_TO_HSM_MODE_MAP[params.mode]) {
    logInfo "Setting HSM to ${RING_TO_HSM_MODE_MAP[params.mode]}"
    logTrace "mode: ${params.mode} hsmStatus: ${location.hsmStatus}"
    sendLocationEvent(name: "hsmSetArm", value: RING_TO_HSM_MODE_MAP[params.mode])
  }
  if (cancelAlertsOnDisarm && params.mode == "off") {
    sendLocationEvent(name: "hsmSetArm", value: "cancelAlerts")
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
