/**
 *  Ring Virtual Lock Driver
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
 *  2019-05-06: Initial
 *
 */

import groovy.json.JsonSlurper

metadata {
  definition(name: "Ring Virtual Lock", namespace: "codahq-hubitat", author: "Ben Rimmasch") {
    capability "Refresh"
    capability "Sensor"
    capability "Lock"
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

def lock() {
  logDebug "lock()"
  parent.simpleRequest("setlock", [mode: "lock", zid: device.getDataValue("zid"), dst: device.getDataValue("dst")])
}

def unlock() {
  parent.simpleRequest("setlock", [mode: "unlock", zid: device.getDataValue("zid"), dst: device.getDataValue("dst")])
}

def setValues(params) {
  logDebug "setValues(params)"
  logTrace "params: ${params}"
  if (params.lock && device.currentValue("lock") != params.lock) {
    logInfo "Device ${device.label} is ${params.lock}"
    sendEvent(name: "lock", value: params.lock)
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
