/**
 *  Ring Virtual Keypad Driver
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
  definition(name: "Ring Virtual Keypad", namespace: "codahq-hubitat", author: "Ben Rimmasch") {
    capability "Sensor"
    capability "Motion Sensor"
    capability "Audio Volume"
  }

  preferences {
    input name: "motionTimeout", type: "number", range: 15..600, title: "Time in seconds before motion resets to inactive", defaultValue: 60
    input name: "descriptionTextEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: false
    input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false
    input name: "traceLogEnable", type: "bool", title: "Enable trace logging", defaultValue: false
  }
}

private getVOLUME_INC() {
  return 5 //somebody can make this a preference if they feel strongly about it
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

def setVolume(vol) {
  logDebug "Attempting to set volume."
  vol > 100 ? 100 : vol
  vol < 0 ? 0 : vol
  if (vol == 0 && !isMuted()) {
    state.prevVolume = device.currentValue("volume")
    sendEvent(name: "mute", value: "muted")
  }
  else if (vol != 0 && isMuted()) {
    sendEvent(name: "mute", value: "unmuted")
  }
  else {
    logDebug "No mute/unmute needed..."
  }
  if (device.currentValue("volume") != vol) {
    parent.simpleRequest("set-volume-keypad", [dst: device.getDataValue("zid"), volume: vol])
  }
  else {
    logInfo "Already at volume."
    sendEvent(name: "volume", value: device.currentValue("volume"))
  }
}

def volumeUp() {
  logDebug "Attempting to raise volume."
  def nextVol = device.currentValue("volume") + VOLUME_INC
  if (nextVol <= 100) {
    setVolume(nextVol)
  }
  else {
    logInfo "Already max volume."
    sendEvent(name: "volume", value: device.currentValue("volume"))
  }
}

def volumeDown() {
  logDebug "Attempting to lower volume."
  def nextVol = device.currentValue("volume") - VOLUME_INC
  if (nextVol >= 0) {
    setVolume(nextVol)
  }
  else {
    logInfo "Already min volume."
    sendEvent(name: "volume", value: device.currentValue("volume"))
  }
}

def mute() {
  logDebug "Attempting to mute."
  setVolume(0)
}

def unmute() {
  logDebug "Attempting to unmute."
  setVolume(state.prevVolume)
}

private isMuted() {
  return device.currentValue("mute") == "muted"
}

def refresh() {
  logDebug "Attempting to refresh."
  parent.simpleRequest("refresh", [dst: device.deviceNetworkId])
}

def stopMotion() {
  sendEvent([name: "motion", value: "inactive"])
}

def setValues(params) {
  if (params.volume != null) {
    sendEvent([name: "volume", value: params.volume])
  }
  if (params.motion) {
    unschedule()
    sendEvent([name: "motion", value: params.motion])
    runIn(motionTimeout.toInteger(), stopMotion)
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

def childParse(type, params = []) {
  logDebug "childParse(type, params)"
  logTrace "type ${type}"
  logTrace "params ${params}"

}
