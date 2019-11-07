/**
 *  Ring Beams Bridge Driver
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
import groovy.json.JsonOutput

metadata {
  definition(name: "Ring Beams Bridge", namespace: "codahq-hubitat", author: "Ben Rimmasch") {
    capability "Refresh"
    capability "Sensor"

    command "createDevices"
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

def createDevices() {
  logDebug "Attempting to create devices."
  parent.createDevices(device.getDataValue("zid"))
}

def refresh() {
  logDebug "Attempting to refresh."
  parent.refresh(device.getDataValue("zid"))
}

def setValues(deviceInfo) {
  logDebug "updateDevice(deviceInfo)"
  logTrace "deviceInfo: ${JsonOutput.prettyPrint(JsonOutput.toJson(deviceInfo))}"

  if (deviceInfo.lastUpdate) {
    state.lastUpdate = deviceInfo.lastUpdate
  }
  if (deviceInfo.impulseType) {
    state.impulseType = deviceInfo.impulseType
  }
  if (deviceInfo.lastCommTime) {
    state.signalStrength = deviceInfo.lastCommTime
  }
  if (deviceInfo.state?.networks?.wlan0) {
    state.network = deviceInfo.state?.networks?.wlan0.ssid
    state.rssi = deviceInfo.state?.networks?.wlan0.rssi
  }
  //Ring deprecated version info in favor of a single int version
  /*
  if (deviceInfo.state?.version?.buildNumber && device.getDataValue("buildNumber") != deviceInfo.state?.version?.buildNumber) {
    device.updateDataValue("buildNumber", deviceInfo.state?.version?.buildNumber)
  }
  if (deviceInfo.state?.version?.nordicFirmwareVersion && device.getDataValue("nordicFirmwareVersion") != deviceInfo.state?.version?.nordicFirmwareVersion) {
    device.updateDataValue("nordicFirmwareVersion", deviceInfo.state?.version?.nordicFirmwareVersion)
  }
  if (deviceInfo.state?.version?.softwareVersion && device.getDataValue("softwareVersion") != deviceInfo.state?.version?.softwareVersion) {
    device.updateDataValue("softwareVersion", deviceInfo.state?.version?.softwareVersion)
  }
  */
  if (deviceInfo.state?.version && device.getDataValue("version") != deviceInfo.state?.version) {
    device.updateDataValue("version", deviceInfo.state?.version.toString())
  }
}

def checkChanged(attribute, newStatus) {
  if (device.currentValue(attribute) != newStatus) {
    logInfo "${attribute.capitalize()} for device ${device.label} is ${newStatus}"
    sendEvent(name: attribute, value: newStatus)
  }
}
