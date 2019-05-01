/**
 *  Ring Z-Wave Adapter
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
  definition(name: "Ring Z-Wave Adapter", namespace: "codahq-hubitat", author: "Ben Rimmasch") {
    //capability "Refresh"
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
  parent.simpleRequest("refresh", [dst: device.deviceNetworkId])
}

def setValues(params) {
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

def childParse(type, params = []) {
  logDebug "childParse(type, params)"
  logTrace "type ${type}"
  logTrace "params ${params}"

}
