/**
 *  Ring Generic Chime Device Driver
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
  definition(name: "Ring Generic Chime", namespace: "codahq-hubitat", author: "Ben Rimmasch") {
    capability "Actuator"
    capability "Tone"
    capability "AudioNotification"
    capability "AudioVolume"
    capability "Refresh"
    capability "Polling"

    command "playDing"
    command "playMotion"
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
  logDebug "configure()"
  refresh()
}

def parse(String description) {
  logDebug "description: ${description}"
}

def poll() {
  logDebug "poll()"
  refresh()
}

def refresh() {
  logDebug "Attempting to refresh."
  parent.simpleRequest("refresh-device", [dni: device.deviceNetworkId])
}

def beep() {
  logDebug "Attempting to beep."
  if (!isMuted()) {
    parent.simpleRequest("chime-motion", [dni: device.deviceNetworkId])
  }
  else {
    logInfo "No beep because muted"
  }
}

def playMotion() {
  logDebug "Attempting to play motion."
  if (!isMuted()) {
    parent.simpleRequest("chime-motion", [dni: device.deviceNetworkId])
  }
  else {
    logInfo "No motion because muted"
  }
}

def playDing() {
  logDebug "Attempting to play ding."
  if (!isMuted()) {
    parent.simpleRequest("chime-ding", [dni: device.deviceNetworkId])
  }
  else {
    logInfo "No ding because muted"
  }
}

def mute() {
  logDebug "Attempting to mute."
  if (!isMuted()) {
    state.prevVolume = device.currentValue("volume")
    parent.simpleRequest("chime-mute", [dni: device.deviceNetworkId])
  }
  else {
    logInfo "Already muted."
    sendEvent(name: "mute", value: "muted")
  }
}

def unmute() {
  logDebug "Attempting to unmute."
  if (isMuted()) {
    parent.simpleRequest("chime-unmute", [dni: device.deviceNetworkId, volume: (state.prevVolume / 10 as Integer)])
  }
  else {
    logInfo "Already unmuted."
    sendEvent(name: "mute", value: "unmuted")
  }
}

def setVolume(volumelevel) {
  logDebug "Attempting to set volume."
  if (device.currentValue("volume") != volumelevel) {
    parent.simpleRequest("chime-volume", [dni: device.deviceNetworkId, volume: (volumelevel / 10 as Integer)])
  }
  else {
    logInfo "Already at volume."
    sendEvent(name: "volume", value: device.currentValue("volume"))
  }
}

def volumeUp() {
  logDebug "Attempting to raise volume."
  def currVol = device.currentValue("volume") / 10 as Integer
  if (currVol < 10) {
    setVolume((currVol + 1) * 10)
  }
  else {
    logInfo "Already max volume."
    sendEvent(name: "volume", value: device.currentValue("volume"))
  }
}

def volumeDown() {
  logDebug "Attempting to lower volume."
  def currVol = device.currentValue("volume") / 10 as Integer
  if (currVol > 0) {
    setVolume((currVol - 1) * 10)
  }
  else {
    logInfo "Already min volume."
    sendEvent(name: "volume", value: device.currentValue("volume"))
  }
}

//TODO
/*
playText(text, volumelevel)
text required (STRING) - Text to play
volumelevel optional (NUMBER) - Volume level (0 to 100)
playTextAndRestore(text, volumelevel)
text required (STRING) - Text to play
volumelevel optional (NUMBER) - Volume level (0 to 100)
playTextAndResume(text, volumelevel)
text required (STRING) - Text to play
volumelevel optional (NUMBER) - Volume level (0 to 100)
*/

def playTrack(trackuri, volumelevel) {
  log.error "Not implemented! playTrack(trackuri, volumelevel)"
}

def playTrackAndRestore(trackuri, volumelevel) {
  log.error "Not implemented! playTrackAndRestore(trackuri, volumelevel)"
}

def playTrackAndResume(trackuri, volumelevel) {
  log.error "Not implemented! playTrackAndResume(trackuri, volumelevel)"
}

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

private isMuted() {
  return device.currentValue("mute") == "muted"
}
