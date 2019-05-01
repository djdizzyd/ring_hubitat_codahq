/**
 *  Ring Alarm Device Driver
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
 *  2019-03-24: Initial
 *
 */

import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import hubitat.helper.InterfaceUtils

metadata {
  definition(name: "Ring API Virtual Device", namespace: "codahq-hubitat", author: "Ben Rimmasch",
          description: "This device holds the websocket connection that controls the alarm hub and/or the lighting bridge") {
    capability "Actuator"
    capability "Initialize"
    capability "Refresh"

    command "createDevices"
  }

  preferences {
    input name: "descriptionTextEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
    input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
    input name: "traceLogEnable", type: "bool", title: "Enable trace logging", defaultValue: true
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
}

def parse(String description) {
  //logDebug "parse(description)"
  //logTrace "description: ${description}"
  if (description.equals("2")) {
    //keep alive
    sendMsg("2")
  }
  else if (description.equals("3")) {
    //Do nothing. keep alive response
  }
  else if (description.startsWith(MESSAGE_PREFIX)) {
    def msg = description.substring(MESSAGE_PREFIX.length())
    def slurper = new groovy.json.JsonSlurper()
    def json = slurper.parseText(msg)
    //logTrace "json: $json"
    if (json[0].equals("DataUpdate")) {
      handleUpdate(json[1])
    }
    else if (json[0].equals("message") && json[1].msg == "DeviceInfoDocGetList" && json[1].datatype == "DeviceInfoDocType") {
      handleRefresh(json[1].body, state.createDevices, json[1].src)
      if (state.createDevices) state.createDevices = false
    }
    else if (json[0].equals("message") && json[1].msg == "DeviceInfoSet") {
      if (json[1].status == 0) {
        logTrace "DeviceInfoSet with seq ${json[1].seq} succeeded."
      }
      else {
        log.warn "I think a DeviceInfoSet failed?"
        log.warn description
      }
    }
    else {
      log.warn "huh? what's this?"
      log.warn description
    }
  }
}

def initialize() {
  logDebug "initialize()"
  //parent.simpleRequest("ws-connect", [dni: device.deviceNetworkId])
  parent.simpleRequest("tickets", [dni: device.deviceNetworkId])
  state.seq = 0
}

def refresh() {
  //def dst = "***REMOVED***"
  state.hubs?.each { hub ->
    if (hub.kind == "base_station_v1") {
      logTrace "refresh(${hub.zid})"
      simpleRequest("refresh", [dst: hub.zid])
    }
  }
}

/**
 * This will create all devices possible. If the user doesn't want some of them they will have to delete them manually for now.
 * The reason I had to do it this way is because I couldn't find a non websockets API call to get a list of alarm devices.
 */
def createDevices() {
  state.createDevices = true
  refresh()
}

def childParse(type, params = []) {
  logDebug "childParse(type, params)"
  logTrace "type ${type}"
  logTrace "params ${params}"

  if (type == "ws-connect" || type == "tickets") {
    initWebsocket(params.msg)
    //42["message",{"msg":"RoomGetList","dst":"***REMOVED***","seq":1}]
  }
  else {
    log.error "Unhandled type ${type}"
  }
}


def simpleRequest(type, params = [:]) {
  logDebug "simpleRequest(${type})"
  logTrace "params: ${params}"

  def request = JsonOutput.toJson(getRequests(params).getAt(type))
  logTrace "request: ${request}"

  if (request == null) {
    return
  }

  try {
    sendMsg(MESSAGE_PREFIX + request)
  }
  catch (e) {
    log.warn "exception: ${e} cause: ${ex.getCause()}"
    log.warn "request type: ${type} request: ${request}"
  }
}

private getRequests(parts) {
  //logTrace "getRequest(parts)"
  //logTrace "parts: ${parts} ${parts.dni}"
  state.seq = (state.seq ?: 0) + 1 //annoyingly the code editor doesn't like the ++ operator
  return [
          "refresh" : ["message", [msg: "DeviceInfoDocGetList", dst: parts.dst, seq: state.seq]],
          "set-mode": [
                  "message", [
                  msg     : "DeviceInfoSet",
                  datatype: "DeviceInfoSetType",
                  body    : [
                          [
                                  zid    : parts.zid,
                                  command: [
                                          v1: [
                                                  [
                                                          commandType: "security-panel.switch-mode",
                                                          data       : ["mode": parts.mode]
                                                  ]
                                          ]
                                  ]
                          ]
                  ],
                  dst     : parts.dst,
                  seq     : state.seq
          ]
          ]
  ]
}

def sendMsg(String s) {
  InterfaceUtils.sendWebSocketMessage(device, s)
}

def webSocketStatus(String status) {
  logDebug "webSocketStatus- ${status}"

  if (status.startsWith('failure: ')) {
    log.warn("failure message from web socket ${status}")
    reconnectWebSocket()
  }
  else if (status == 'status: open') {
    logInfo "websocket is open"
    // success! reset reconnect delay
    pauseExecution(1000)
    state.reconnectDelay = 1
  }
  else if (status == "status: closing") {
    log.warn "WebSocket connection closing."
  }
  else {
    log.warn "WebSocket error, reconnecting."
    reconnectWebSocket()
  }
}

def initWebsocket(json) {
  logDebug "initWebsocket(json)"
  logTrace "json: ${json}"

  def wsUrl
  if (json.server) {
    wsUrl = "wss://${json.server}/socket.io/?authcode=${json.authCode}&ack=false&EIO=3&transport=websocket"
  }
  else if (json.host) {
    wsUrl = "wss://${json.host}/socket.io/?authcode=${json.ticket}&ack=false&EIO=3&transport=websocket"
    state.hubs = json.assets.collect { hub ->
      [doorbotId: hub.doorbotId, kind: hub.kind, zid: hub.uuid]
    }
  }
  else {
    log.error "Can't find the server: ${json}"
  }

  //test client: https://www.websocket.org/echo.html
  logTrace "wsUrl: $wsUrl"

  try {
    InterfaceUtils.webSocketConnect(device, wsUrl)
    logInfo "Connected!"
    refresh()
  }
  catch (e) {
    logDebug "initialize error: ${e.message} ${e}"
    log.error "WebSocket connect failed"
  }
}

def reconnectWebSocket() {
  // first delay is 2 seconds, doubles every time
  state.reconnectDelay = (state.reconnectDelay ?: 1) * 2
  // don't let delay get too crazy, max it out at 10 minutes
  if (state.reconnectDelay > 600) state.reconnectDelay = 600

  //If the socket is unavailable, give it some time before trying to reconnect
  runIn(state.reconnectDelay, initialize)
}

private handleUpdate(update) {
  //logDebug "handleUpdate(update)"
  //logTrace "update: $update"
  if (update.msg == "DataUpdate" && update.datatype == "DeviceInfoDocType") {
    handleDeviceInfo(update.body[0], update.src)
  }
  else if (update.msg == "Passthru" && update.datatype == "PassthruType") {
    handleCountdown(update.body[0], update.src)
  }
  else if (update.msg == "SessionInfo" && update.datatype == "SessionInfoType") {
    handleSessionInfo(update.body[0])
  }
  else {
    log.warn "update not handled"
    log.warn JsonOutput.prettyPrint(JsonOutput.toJson(update))
  }
}

private getMODES() {
  return [
          "none": "off",
          "some": "home",
          "all" : "away"
  ]
}

//Device
private handleDeviceInfo(info, src) {
  //logDebug "handleDeviceInfo(info)"
  //logTrace "info: $info"
  try {
    if (info.general.v2.adapterType == "zwave" || (info.general.v2.adapterType == "none" && info.general.v2.deviceType == 'security-panel')) {
      logDebug "Z-wave update from device ${info.context.v1.deviceName} with catalogId ${info.general.v2.catalogId} and zid ${info.general.v2.zid}"
      //log.trace "info: $info"
      def d = getChildDevices()?.find {
        it.deviceNetworkId == getFormattedDNI(info.general.v2.zid)
      }
      if (!d) {
        logDebug "no device for ${info.context.v1.deviceName} with zid ${info.general.v2.zid}"
        //logTrace "info: ${JsonOutput.prettyPrint(JsonOutput.toJson(info))}"
        //TODO: more device types
      }
      else if (!info.impulse && !info.device) {
        logTrace "lonely update?  should I just handle these as heartbeats even though they don't have an impulse or device?"
      }
      else if (info.impulse?.v1[0]?.impulseType == "comm.heartbeat"
              || info.impulse?.v1[0]?.impulseType == "comm.wakeup"
              || info.impulse?.v1[0]?.impulseType == "error.comm.wakeup-missed") {
        handleHeartbeat(d, info)
      }
      else if (d.getDataValue("type") == "sensor.contact" && info.device?.v1) {
        d.setValues(["contact": info.device.v1.faulted ? "open" : "closed"])
      }
      else if (d.getDataValue("type") == "sensor.motion" && info.device?.v1) {
        d.setValues(["motion": info.device.v1.faulted ? "active" : "inactive"])
      }
      else if (d.getDataValue("type") == "security-panel" && info.device?.v1) {
        if (info.device.v1.transitionDelayEndTimestamp && info.general?.v2?.lastUpdate) {
          def secs = (info.device.v1.transitionDelayEndTimestamp - info.general.v2.lastUpdate) / 1000
          logInfo "Ring Alarm will exit delay in ${secs} seconds"
        }
        d.setValues(["mode": MODES["${info.device.v1.mode}"]])
      }
      else {
        d.properties.each { log.warn it }
        log.warn "info not handled for device ${d} with type ${d.getDataValue("type")}"
        log.warn JsonOutput.prettyPrint(JsonOutput.toJson(info))
      }
    }
    else if (info.general.v2.adapterType == "ringnet") {
      logDebug "${info.general.v2.adapterType} update from device ${info.context.v1.deviceName} with zid ${info.general.v2.zid}"
      //stub for unreleased beams hardware
    }
    else if (info.general.v2.adapterType == "none") {
      logDebug "Update from device ${info.context.v1.deviceName} with zid ${info.general.v2.zid}"
      //not impleted yet
    }
    else {
      logTrace "type not handled ${info.general.v2.adapterType} from source ${src}"
      logTrace JsonOutput.prettyPrint(JsonOutput.toJson(info))
    }
  }
  catch (e) {
    log.error "handleDeviceInfo error: ${e.message} ${e}"
    log.error "info: ${JsonOutput.prettyPrint(JsonOutput.toJson(info))}"
  }
}

//Countdown
private handleCountdown(info, src) {
  //logDebug "handleCountdown(info)"
  //logTrace "info: $info"
  try {
    if (info.data) {
      logDebug "Passthru update ${info.type} for zid ${info.zid} with remaining/total ${info.data.timeLeft}/${info.data.total} and transition ${info.data.transition}"
      //log.trace "info: $info"
      def d = getChildDevices()?.find {
        it.deviceNetworkId == getFormattedDNI(info.zid)
      }
      if (!d) {
        logDebug "no countdown from ${src} for device zid ${info.zid}"
        //logTrace "info: ${JsonOutput.prettyPrint(JsonOutput.toJson(info))}"
        //TODO: more device types
      }
      else {
        logDebug "tick: remaining/total ${info.data.timeLeft}/${info.data.total}"
      }
    }
    else {
      logTrace "passthru not handled ${info.zid} from source ${src}"
      logTrace JsonOutput.prettyPrint(JsonOutput.toJson(info))
    }
  }
  catch (e) {
    log.error "handleCountdown error: ${e.message} ${e}"
    log.error "info: ${JsonOutput.prettyPrint(JsonOutput.toJson(info))}"
  }
}

//Session
private handleSessionInfo(session) {
  logInfo "Connected for a ${session.kind} with doorbotId ${session.doorbotId} and assetUuid ${session.assetUuid}"
}

private handleHeartbeat(d, info) {
  logDebug "handleHeartbeat(d, info)"
  logTrace "type: ${info.impulse?.v1[0]?.impulseType}"
  //logTrace "info: ${JsonOutput.prettyPrint(JsonOutput.toJson(info))}"
  try {
    def params = [:]
    if (info.general.v2.lastUpdate) {
      params << ["lastUpdate": info.general.v2.lastUpdate]
    }
    if (info.impulse?.v1[0]?.impulseType) {
      params << ["impulseType": info.impulse?.v1[0]?.impulseType]
    }
    if (info.general.v2.lastCommTime) {
      params << ["lastCommTime": info.general.v2.lastCommTime]
    }
    if (info.general.v2.nextExpectedWakeup) {
      params << ["nextExpectedWakeup": info.general.v2.nextExpectedWakeup]
    }
    if (info.context.v1.adapter.v1.signalStrength) {
      params << ["signalStrength": info.context.v1.adapter.v1.signalStrength]
    }
    if (info.context.v1.adapter.v1.fingerprint.firmware) {
      params << ["firmware": "${info.context.v1.adapter.v1.fingerprint.firmware.version}.${info.context.v1.adapter.v1.fingerprint.firmware.subversion}"]
    }
    if (info.context.v1.adapter.v1.fingerprint.hardwareVersion) {
      params << ["hardwareVersion": "${info.context.v1.adapter.v1.fingerprint.hardwareVersion}"]
    }
    logTrace "params: ${params}"
    d.setValues(params)
  }
  catch (e) {
    log.error "handleHeartbeat error: ${e.message} ${e}"
    log.error "info: ${JsonOutput.prettyPrint(JsonOutput.toJson(info))}"
  }
}

private handleRefresh(body, create = false, src) {
  logDebug "handleRefresh(body, create == ${create}, src == ${src})"
  //logTrace "body: ${JsonOutput.prettyPrint(JsonOutput.toJson(body))}"

  def sensors = body.findAll { dev ->
    (dev.general.v2.adapterType == 'zwave' && DEVICE_TYPES["${dev.general.v2.deviceType}"]) || (dev.general.v2.adapterType == 'none' && DEVICE_TYPES["${dev.general.v2.deviceType}"])
  }
  refreshSensors(sensors, create, src)
}

private refreshSensors(sensors, create, src) {
  sensors.each { sensor ->
    if (create) {
      logDebug "Found ${sensor.general.v2.name}"
      //logTrace "sensor: ${JsonOutput.prettyPrint(JsonOutput.toJson(sensor))}"
      logTrace "general sensor info ::: catalogId: ${sensor.general.v2.catalogId}, zid: ${sensor.general.v2.zid},"
      logTrace "deviceFoundTime: ${sensor.general.v2.deviceFoundTime}, deviceType: ${sensor.general.v2.deviceType}, fingerprint: ${sensor.general.v2.fingerprint},"
      logTrace "manufacturerName: ${sensor.general.v2.manufacturerName}, serialNumber: ${sensor.general.v2.serialNumber}, tamperStatus: ${sensor.general.v2.tamperStatus},"
    }

    def d
    if (sensor.general.v2.zid) {
      d = getChildDevices()?.find {
        it.deviceNetworkId == getFormattedDNI(sensor.general.v2.zid)
      }
    }

    if (create) {
      if (!d) {
        log.warn "Creating a ${sensor.general.v2.name} (${sensor.general.v2.deviceType}) with dni: ${getFormattedDNI(sensor.general.v2.zid)}"

        try {
          d = addChildDevice("codahq-hubitat", DEVICE_TYPES[sensor.general.v2.deviceType].name, getFormattedDNI(sensor.general.v2.zid), [
                  //"label": sensor.general.v2.name,
                  "zid"         : sensor.general.v2.zid,
                  "fingerprint" : sensor.general.v2.fingerprint ?: "N/A",
                  "manufacturer": sensor.general.v2.manufacturerName ?: "Ring",
                  "serial"      : sensor.general.v2.serialNumber ?: "N/A",
                  "type"        : sensor.general.v2.deviceType,
                  "dst"         : src
          ])
          d.label = sensor.general.v2.name
          log.warn "Succesfully added ${sensor.general.v2.deviceType} with dni: ${getFormattedDNI(sensor.general.v2.zid)}"
        }
        catch (e) {
          log.error "An error occured ${e}"
        }
      }
      else {
        logDebug "No need to create device ${d}"
      }
    }
    if (d) {
      logTrace "state sensor info for ${sensor.general.v2.name} ::: faulted: ${sensor.device.v1.faulted}, batteryLevel: ${sensor.general.v2.batteryLevel}"
      if (sensor.general.v2.deviceType == "sensor.contact") {
        d.setValues(["contact": sensor.device.v1.faulted ? "open" : "closed"])
      }
      if (sensor.general.v2.deviceType == "sensor.motion") {
        d.setValues(["motion": sensor.device.v1.faulted ? "active" : "inactive"])
      }
      d.setValues(["battery": sensor.general.v2.batteryLevel])
    }

  }
}

def uninstalled() {
  getChildDevices().each {
    deleteChildDevice(it.deviceNetworkId)
  }
}

private getMESSAGE_PREFIX() {
  return "42"
}

private getDEVICE_TYPES() {
  return [
          "sensor.contact": [name: "Ring Generic Contact Sensor"],
          "sensor.motion" : [name: "Ring Generic Motion Sensor"],
          "adapter.zwave" : [name: "Ring Z-Wave Adapter"],
          "security-panel": [name: "Ring Alarm Base Station"]
  ]
}

def String getFormattedDNI(id) {
  return "RING||${id}"
}

def String getRingDeviceId(dni) {
  //logDebug "getRingDeviceId(dni)"
  //logTrace "dni: ${dni}"
  return dni?.split("||")?.getAt(1)
}
