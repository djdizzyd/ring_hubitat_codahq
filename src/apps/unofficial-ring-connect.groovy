/**
 * 	Completely Unoffical Ring Connect App For Floodlights/Spotlights/Chimes Only (Don't hate me, Ring guys. I had to do it.)
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

import groovyx.net.http.ContentType
import groovy.json.JsonOutput

definition(
  name: "Unofficial Ring Connect",
  namespace: "codahq-hubitat",
  author: "Ben Rimmasch (codahq)",
  description: "Service Manager for Ring Floodlights, Spotlights and Chimes",
  category: "Convenience",
  iconUrl: "https://github.com/fake/url/what/is/this/for/ic_cast_grey_24dp.png",
  iconX2Url: "https://github.com/fake/url/what/is/this/for/ic_cast_grey_24dp.png",
  iconX3Url: "https://github.com/fake/url/what/is/this/for/ic_cast_grey_24dp.png"
)

preferences {
  page(name: "mainPage")
  page(name: "login")
  page(name: "locations")
  page(name: "configurePDevice")
  page(name: "deletePDevice")
  page(name: "changeName")
  page(name: "discoveryPage", title: "Device Discovery", content: "discoveryPage", refreshTimeout: 10)
  page(name: "addDevices", title: "Add Ring Devices", content: "addDevices")
  page(name: "deviceDiscovery")
}

def login() {
  dynamicPage(name: "login", title: "Log into Your Ring Account", nextPage: "locations", uninstall: true) {
    section("Ring Account Information") {
      preferences {
        input "username", "email", title: "Ring Username", description: "Email used to login to Ring.com", displayDuringSetup: true, required: true
        input "password", "password", title: "Ring Password", description: "Password you login to Ring.com", displayDuringSetup: true, required: true
      }
    }
  }
}

def locations() {

  def locations = simpleRequest("locations")
  def options = [:]
  locations.each {
    def value = "${it.name}"
    def key = "${it.location_id}"
    options["${key}"] = value
  }
  def numFound = options.size()

  dynamicPage(name: "locations", title: "Select which location you want to use", nextPage: "mainPage", uninstall: true) {
    section("Locations") {
      input "selectedLocations", "enum", required: true, title: "Select a locations  (${numFound} found)", multiple: false, options: options
    }
  }
}

def mainPage() {

  getNotifications()

  def all = simpleRequest("locations")

  def locations = []
  all.each {
    location ->
      if (selectedLocations.contains(location.location_id)) {
        locations << location.name
      }
  }

  dynamicPage(name: "mainPage", title: "Manage Your Ring Devices", nextPage: null, uninstall: true, install: true) {
    section("Ring Account Information    (<b>${authenticate() ? 'Successfully Logged In!' : 'Not Logged In. Please Configure!'}</b>)") {
      href "login", title: "Log into Your Ring Account", description: ""
    }

    section("Configure Devices For Location:    <b>${locations.join(", ")}</b>") {
      href "deviceDiscovery", title: "Discover Devices", description: ""
    }

    section("Installed Devices") {
      getChildDevices().sort({ a, b -> a["deviceNetworkId"] <=> b["deviceNetworkId"] }).each {
        href "configurePDevice", title: "$it.label", description: "", params: [did: it.deviceNetworkId]
      }
    }

    section("Logging") {
      preferences {
        input name: "descriptionTextEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: false
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false
        input name: "traceLogEnable", type: "bool", title: "Enable trace logging", defaultValue: false
      }
    }
  }
}

def configurePDevice(params) {
  if (params?.did || params?.params?.did) {
    if (params.did) {
      state.currentDeviceId = params.did
      state.currentDisplayName = getChildDevice(params.did)?.displayName
    }
    else {
      state.currentDeviceId = params.params.did
      state.currentDisplayName = getChildDevice(params.params.did)?.displayName
    }
  }
  if (getChildDevice(state.currentDeviceId) != null) getChildDevice(state.currentDeviceId).configure()
  dynamicPage(name: "configurePDevice", title: "Configure Ring Devices created with this app", nextPage: "mainPage") {
    section {
      app.updateSetting("${state.currentDeviceId}_label", getChildDevice(state.currentDeviceId).label)
      input "${state.currentDeviceId}_label", "text", title: "Device Name", description: "", required: false
      href "changeName", title: "Change Device Name", description: "Edit the name above and click here to change it"
    }
    if (state.currentDeviceId == getFormattedDNI(RING_API_DNI)) {
      section {
        paragraph("This is the virtual device that holds the WebSockets connection for your Ring hubs/bridges. You don't need to "
          + "know what this means but I wanted to tell you so I can justify why it had to exist and why you have to create "
          + "it.  At the time of the creation of this app a WebSockets client could only be created in a device.  It is/was "
          + "not available to apps.")
        paragraph("To keep complexity low (for now) you must navigate to the API device and create all devices manually.  If there "
          + "is a device that you don't want simply delete it.  If a device is not created that probably means the device "
          + "driver for it was not installed or is not yet created for that device type. If Ring adds or I find an API call "
          + "that can list hub devices (Z-Wave and Beams) with their ZIDs then I will add functionality to create and "
          + "maintain those types of devices through the HE app.  For now, the only way I can get a list of these devices "
          + "is through the web socket so it will only be done through the device.")
      }
    }
    section {
      href "deletePDevice", title: "Delete $state.currentDisplayName", description: ""
    }
  }
}

def deletePDevice() {
  try {
    unsubscribe()
    deleteChildDevice(state.currentDeviceId)
    dynamicPage(name: "deletePDevice", title: "Deletion Summary", nextPage: "mainPage") {
      section {
        paragraph "The device has been deleted. Press next to continue"
      }
    }

  }
  catch (e) {
    dynamicPage(name: "deletePDevice", title: "Deletion Summary", nextPage: "mainPage") {
      section {
        paragraph "Error: ${(e as String).split(": ")[1]}."
      }
    }
  }
}

def changeName() {
  def thisDevice = getChildDevice(state.currentDeviceId)
  thisDevice.label = settings["${state.currentDeviceId}_label"]

  dynamicPage(name: "changeName", title: "Change Name Summary", nextPage: "mainPage") {
    section {
      paragraph "The device has been renamed. Press \"Next\" to continue"
    }
  }
}

def discoveryPage() {
  return deviceDiscovery()
}

def deviceDiscovery(params = [:]) {
  logDebug "deviceDiscovery(params=[:])"

  def auth_token = authenticate()

  if (!auth_token) {
    return dynamicPage(name: "deviceDiscovery", title: "Authenticate failed!  Please check your Ring username and password", nextPage: "login", uninstall: true) {
    }
  }

  if (!selectedLocations) {
    return dynamicPage(name: "deviceDiscovery", title: "No locations selected!  Please check your Ring location setup", nextPage: "login", uninstall: true) {
    }
  }

  if (params.reset == "true") {
    logDebug "Cleaning old device memory"
    state.devices = [:]
    app.updateSetting("selectedDevice", "")
  }

  discoverDevices()
  def devices = devicesDiscovered()

  logTrace "devices ${devices}"

  def options = devices ?: []
  def numFound = options.size() ?: 0

  return dynamicPage(name: "deviceDiscovery", title: "Discovery Started!", nextPage: "addDevices", uninstall: true) {
    section("Making a call to Ring.  Are these your devices?  Please select the devices you want created as Hubitat devices.") {
      input "selectedDevices", "enum", required: false, title: "Select Ring Device(s) (${numFound} found)", multiple: true, options: options
    }
    section("Options") {
      href "deviceDiscovery", title: "Reset list of discovered devices", description: "", params: ["reset": "true"]
    }
  }
}

Map devicesDiscovered() {
  def vdevices = getDevices()

  logTrace "vdevices ${vdevices}"

  def map = [:]
  vdevices.each {
    def value = "${it.name}"
    def key = "${it.id}"
    map["${key}"] = map["${key}"] ? map["${key}"] + " || " + value : value
  }
  map
}

private discoverDevices() {
  logDebug "deviceIdReport()"
  def supportedIds = getDeviceIds()
  logTrace "supportedIds ${supportedIds}"
  state.devices = supportedIds
}

def configured() {

}

def installed() {
  initialize()
}

def updated() {
  unsubscribe()
  unschedule()
  initialize()
}

def initialize() {
  logDebug "initialize()"
  if (!state.appDeviceId) {
    generateAppDeviceId()
  }
}

def getDevices() {
  state.devices = state.devices ?: [:]
}

def addDevices() {
  logDebug "addDevices()"

  def devices = getDevices()
  logTrace "devices ${devices}"

  def sectionText = ""

  selectedDevices.each {
    id ->
      bridgeLinking

      logTrace "Selected id ${id}"

      def selectedDevice = devices.find { it.id.toString() == id.toString() }

      logTrace "Selected device ${selectedDevice}"

      def d
      if (selectedDevice) {
        d = getChildDevices()?.find {
          it.deviceNetworkId == getFormattedDNI(selectedDevice.id)
        }
      }

      if (!d) {
        logDebug selectedDevice
        log.warn "Creating a ${DEVICE_TYPES[selectedDevice.kind].name} with dni: ${getFormattedDNI(selectedDevice.id)}"

        try {
          def newDevice = addChildDevice("codahq-hubitat", DEVICE_TYPES[selectedDevice.kind].driver, getFormattedDNI(selectedDevice.id), selectedDevice?.hub, [
            "label": selectedDevice.id == RING_API_DNI ? DEVICE_TYPES[selectedDevice.kind].driver : (selectedDevice?.name ?: DEVICE_TYPES[selectedDevice.kind].name),
            "data": [
              "device_id": selectedDevice.id
            ]
          ])
          if (selectedDevice.id == RING_API_DNI) {
            //init the websocket connection and set seq to 0
            newDevice.initialize()
          }
          newDevice.refresh()

          sectionText = sectionText + "Succesfully added ${DEVICE_TYPES[selectedDevice.kind].name} with DNI ${getFormattedDNI(selectedDevice.id)} \r\n"
        }
        catch (e) {
          sectionText = sectionText + "An error occured ${e} \r\n"
        }
      }
  }

  logDebug sectionText
  return dynamicPage(name: "addDevices", title: "Devices Added", nextPage: "mainPage", uninstall: true) {
    if (sectionText != "") {
      section("Add Ring Device Results:") {
        paragraph sectionText
      }
    }
    else {
      section("No devices added") {
        paragraph "All selected devices have previously been added"
      }
    }
  }
}

def uninstalled() {
  getChildDevices().each {
    deleteChildDevice(it.deviceNetworkId)
  }
}

def getDeviceIds() {
  logDebug "getDeviceIds"
  def json = simpleRequest("devices")

  return json.inject([]) {
    acc, node ->
      //logTrace "found a ${node ?.kind} at location ${node?.location_id}"
      if (DEVICE_TYPES[node?.kind] && selectedLocations.contains(node?.location_id)) {
        def nodeId = node.kind == "base_station_v1" || node.kind == "beams_bridge_v1" ? RING_API_DNI : node.id
        acc << [name: "${DEVICE_TYPES[node.kind].name} - ${node.description}", id: nodeId, kind: node.kind]
      }
      acc
      //Stickup Cam (Patio North) stickup_cam_lunar
      //Spotlight Cam Battery (Side South) stickup_cam_v4
      //Spotlight (Backyard South) hp_cam_v2
      //Floodlight (Backyard North) hp_cam_v1
      //Stickup Cam Elite (Theater) stickup_cam_elite
  }
}

def getNotifications() {
  simpleRequest("subscribe")
  app.properties.each { log.warn it }
}

private getRING_API_DNI() {
  return "WS_API_DNI"
}

private getDEVICE_TYPES() {
  return [
    "hp_cam_v1": [name: "Ring Floodlight Cam", driver: "Generic Ring Light"],
    "hp_cam_v2": [name: "Ring Spotlight Cam Wired", driver: "Generic Ring Light"],
    "chime_pro": [name: "Ring Chime Pro", driver: "Generic Ring Chime"],
    "chime": [name: "Ring Chime", driver: "Generic Ring Chime"],
    "base_station_v1": [name: "Ring Alarm (API Device)", driver: "Ring API Virtual Device"],
    "beams_bridge_v1": [name: "Ring Bridge (API Device)", driver: "Ring API Virtual Device"]
  ]
}

private getGET() {
  return 'httpGet'
}

private getPOST() {
  return 'httpPost'
}

private getPUT() {
  return 'httpPut'
}

private getJSON() {
  return 'application/json'
}

private getTEXT() {
  return 'text/plain'
}

private getFORM() {
  return 'application/x-www-form-urlencoded'
}

private getALL() {
  return '*/*'
}

private getRequests(parts) {
  //logTrace "getRequest(parts)"
  //logTrace "parts: ${parts} ${parts.dni}"
  return [
    "refresh": [method: GET, params: [uri: "https://api.ring.com", path: "/clients_api/ring_devices", contentType: JSON]],
    "devices": [method: GET, params: [uri: "https://api.ring.com", path: "/clients_api/ring_devices", contentType: JSON]],
    "light-on": [method: PUT, params: [uri: "https://api.ring.com", path: "/clients_api/doorbots/${getRingDeviceId(parts.dni)}/floodlight_light_on", contentType: TEXT]],
    "light-off": [method: PUT, params: [uri: "https://api.ring.com", path: "/clients_api/doorbots/${getRingDeviceId(parts.dni)}/floodlight_light_off", contentType: TEXT]],
    "siren-on": [method: PUT, params: [uri: "https://api.ring.com", path: "/clients_api/doorbots/${getRingDeviceId(parts.dni)}/siren_on", contentType: JSON]],
    "siren-off": [method: PUT, params: [uri: "https://api.ring.com", path: "/clients_api/doorbots/${getRingDeviceId(parts.dni)}/siren_off", contentType: TEXT]],
    "chime-motion": [method: POST, params: [uri: "https://api.ring.com", path: "/clients_api/chimes/${getRingDeviceId(parts.dni)}/play_sound", contentType: TEXT, requestContentType: JSON, body: [kind: "motion"]]],
    "chime-ding": [method: POST, params: [uri: "https://api.ring.com", path: "/clients_api/chimes/${getRingDeviceId(parts.dni)}/play_sound", contentType: TEXT, requestContentType: JSON, body: [kind: "ding"]]],
    "chime-volume": [method: PUT, params: [uri: "https://api.ring.com", path: "/clients_api/chimes/${getRingDeviceId(parts.dni)}", contentType: TEXT, requestContentType: JSON, body: [chime: [settings: [volume: parts.volume]]]]],
    "chime-mute": [method: PUT, params: [uri: "https://api.ring.com", path: "/clients_api/chimes/${getRingDeviceId(parts.dni)}", contentType: TEXT, requestContentType: JSON, body: [chime: [settings: [volume: 0]]]]],
    "chime-unmute": [method: PUT, params: [uri: "https://api.ring.com", path: "/clients_api/chimes/${getRingDeviceId(parts.dni)}", contentType: TEXT, requestContentType: JSON, body: [chime: [settings: [volume: parts.volume]]]]],
    "refresh-device": [method: GET, params: [uri: "https://api.ring.com", path: "/clients_api/ring_devices/${getRingDeviceId(parts.dni)}", contentType: JSON]],
    "locations": [method: GET, type: "bearer", params: [uri: "https://app.ring.com", path: "/rhq/v1/devices/v1/locations", contentType: JSON/*"${JSON}, ${TEXT}, ${ALL}"*/]],
    "ws-connect": [method: POST, type: "bearer", params: [uri: "https://app.ring.com", path: "/api/v1/rs/connections?accountId=${selectedLocations}"], headers: ['Content-Type': "application/x-www-form-urlencoded"]],
    "tickets": [method: GET, type: "bearer", params: [uri: "https://app.ring.com", path: "/api/v1/clap/tickets?locationID=${selectedLocations}", contentType: JSON, requestContentType: TEXT]],
    "subscribe": [method: PUT, type: "bearer", params: [uri: "https://api.ring.com", path: "/clients_api/device", requestContentType: JSON, body: [device: ["push_notification_token": "https://cloud.hubitat.com/api/${location.hubs[0].id}/apps/${app.id}/devices/all?access_token=[maker access token]"]]]],
    "master-key": [method: GET, type: "bearer", params: [uri: "https://app.ring.com", path: "/api/v1/rs/masterkey?locationId=${selectedLocations}", contentType: JSON]]

    //https://cloud.hubitat.com/api/[HUBUID]/apps/937/devices/all?access_token= 10[maker access token]
  ]
}

def simpleRequest(type, params = [:]) {
  logDebug "simpleRequest(${type})"
  logTrace "params: ${params}"
  def auth_token = authenticate()
  logTrace "auth token: ${auth_token}"
  logTrace "bearer token: ${state.access_token}"

  if (!auth_token) {
    log.error "User is not authenticated.  Please check your username and password and try again!"
    return
  }

  def request = getRequests(params).getAt(type)
  logTrace "request: ${request}"

  if (request.type == "bearer") {
    def headers = ['User-Agent': "Dalvik/2.1.0 (Linux; U; Android 6.0.1; Nexus 7 Build/MOB30X)", 'Authorization': "Bearer ${state.access_token}"]
    if (request.headers) {
      headers << request.headers
    }
    request.params << [headers: headers]
  }
  else {
    request.params << [query: [api_version: 9, auth_token: auth_token]]
    request.params << [headers: ['User-Agent': "Dalvik/2.1.0 (Linux; U; Android 6.0.1; Nexus 7 Build/MOB30X)"]]
  }

  if (type == "subscribe") return

  try {

    "${request.method}"(request.params) { resp ->
      logTrace "Success handler"

      def body = resp.data
      if (type == "refresh") {
        logTrace "refeshing from ${body.stickup_cams.size()} cams"

        body.stickup_cams.each {
          def device = getChildDevice(getFormattedDNI(it.id.toString()))
          if (device) {
            device.childParse(type, [msg: it])
          }
        }
      }
      else if (type == "light-on" || type == "light-off" || type == "siren-off") {
        logTrace "resp ${resp}"
        getChildDevice(params.dni).childParse(type, [response: resp.getStatus()])
      }
      else if (type == "devices") {
        //logTrace "resp ${JsonOutput.toJson(body)}"

        body.chimes.each { body.stickup_cams << it }
        body.base_stations.each { body.stickup_cams << it }
        body.beams_bridges.each { body.stickup_cams << it }
        return body.stickup_cams
      }
      else if (type == "chime-motion" || type == "chime-ding" || type == "chime-volume" || type == "chime-mute" || type == "chime-unmute") {
        logTrace "resp ${resp}"
        getChildDevice(params.dni).childParse(type, [response: resp.getStatus(), volume: params.volume])
      }
      else if (type == "refresh-device" || type == "siren-on") {
        logTrace "body ${body}"
        getChildDevice(params.dni).childParse(type, [response: resp.getStatus(), msg: body])
      }
      else if (type == "locations") {
        return body.user_locations
      }
      else if (type == "ws-connect" || type == "tickets") {
        //initWebsocket(body, getChildDevice(params.dni))
        getChildDevice(params.dni).childParse(type, [response: resp.getStatus(), msg: body])
      }
      else if (type == "master-key") {
        getChildDevice(params.dni).childParse(type, [response: resp.getStatus(), msg: body, code: params.code, name: params.name])
      }

    }
  }
  catch (Exception ex) {
    log.warn "exception: ${ex} cause: ${ex.getCause()} status code: ${ex.getStatusCode()} response: ${ex.getResponse()}"
    if (ex.getStatusCode() == 401) {
      state.access_token = "EMPTY"
      state.authentication_token = "EMPTY"
      simpleRequest(type, params)
    }
  }
}

def initWebsocket(json, device) {
  logDebug "initWebsocket(json)"
  logTrace "ws-connect body ${json}"
  //logTrace "app ${app}"
  //logTrace "device ${getChildDevice(params.dni)}"

  try {
    InterfaceUtils.webSocketConnect(device, "wss://${json.server}/?authcode=${json.authCode}&ack=false&EIO=3&transport=websocket")
  }
  catch (e) {
    if (logEnable) log.debug "initialize error: ${e.message}"
    log.error "WebSocket connect failed"
  }
}

def sendMsg(String s) {
  InterfaceUtils.sendWebSocketMessage(device, s)
}

def webSocketStatus(String status) {
  log.debug "webSocketStatus- ${status}"

  if (status.startsWith('failure: ')) {
    log.warn("failure message from web socket ${status}")
    reconnectWebSocket()
  }
  else if (status == 'status: open') {
    log.info "websocket is open"
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

def reconnectWebSocket() {
  // first delay is 2 seconds, doubles every time
  state.reconnectDelay = (state.reconnectDelay ?: 1) * 2
  // don't let delay get too crazy, max it out at 10 minutes
  if (state.reconnectDelay > 600) state.reconnectDelay = 600

  //If the socket is unavailable, give it some time before trying to reconnect
  runIn(state.reconnectDelay, initialize)
}

def parse(String description) {
  logDebug "parse(String description)"
  logTrace "description: $description"
}

def authenticate() {
  if (!state.appDeviceId) {
    generateAppDeviceId()
  }
  def token = getTokens()
  if (!token) {
    state.access_token = "EMPTY"
    state.authentication_token = "EMPTY"
  }
  return getTokens()
}


def getTokens() {
  def s = "${username}:${password}"
  String encodedUandP = s.bytes.encodeBase64()

  if (!state.access_token) state.access_token = "EMPTY"

  if (state.access_token == "EMPTY") {

    def params = [
      uri: "https://oauth.ring.com",
      path: "/oauth/token",
      headers: [
        "User-Agent": "Dalvik/2.1.0 (Linux; U; Android 6.0.1; Nexus 7 Build/MOB30X)"
      ],
      requestContentType: "application/json",
      body: "{\"client_id\": \"ring_official_android\",\"grant_type\": \"password\",\"password\": \"${password}\",\"scope\": \"client\",\"username\": \"${username}\"}"
    ]
    try {
      httpPost(params) {
        resp ->
          logDebug "POST response code: ${resp.status}"

          logDebug "response data: ${resp.data}"
          state.access_token = resp.data.access_token
          state.refresh_token = resp.data.refresh_token
          logDebug "access token: ${state.access_token}"
      }
    }
    catch (e) {
      log.error "HTTP Exception Received on POST: $e"
      log.error "response data: ${resp?.data}"
      return

    }
  }

  if (!state.authentication_token) state.authentication_token = "EMPTY"

  if (state.authentication_token == "EMPTY") {

    params = [
      uri: "https://api.ring.com",
      path: "/clients_api/session",
      headers: [
        Authorization: "Bearer ${state.access_token}",
        "User-Agent": "Dalvik/2.1.0 (Linux; U; Android 6.0.1; Nexus 7 Build/MOB30X)"
      ],
      requestContentType: "application/x-www-form-urlencoded",
      body: "device%5Bos%5D=android&device%5Bhardware_id%5D=${state.appDeviceId}&api_version=9"
    ]

    try {
      httpPost(params) {
        resp ->
          logDebug "POST response code: ${resp?.status}"

          logDebug "response data: ${resp?.data}"
          state.authentication_token = resp?.data?.profile.authentication_token
      }
    }
    catch (e) {
      log.error "HTTP Exception Received on POST: $e"
      log.error "response data: ${resp.data}"
      return

    }

    logDebug "Authenticated, Token Found."

  }
  return state.authentication_token
}

//logging help methods
private logInfo(msg) {
  if (descriptionTextEnable) log.info msg
}

def logDebug(msg) {
  if (logEnable) log.debug msg
}

def logTrace(msg) {
  if (traceLogEnable) log.trace msg
}

def String getFormattedDNI(id) {
  return "RING-${id}"
}

def String getRingDeviceId(dni) {
  //logDebug "getRingDeviceId(dni)"
  //logTrace "dni: ${dni}"
  return dni?.split("-")?.getAt(1)
}

def generateAppDeviceId() {
  //Let's generate an ID so that Ring doesn't think these are all coming from the same device
  def r = new Random()
  def result = (0..<32).collect { r.nextInt(16) }
    .collect { Integer.toString(it, 16).toUpperCase() }
    .join()
  logDebug "Device ID generated: ${result}"
  state.appDeviceId = result
}
