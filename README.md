**Notice!!!** 

First and foremost I need to warn any who venture to use this integration.  This integration is provided for fun without hope of warranty or safety or stable or permanent use. Ring are not official partners with Hubitat or myself and all of the interactions between the Hubitat hub and Ring's cloud servers in this integration are via the private API calls that Ring does not publish or give permission to use for this purpose. I wrote this integration for fun and I do not trust it with my safety. It's a hobby project. (That said, it is well built and mostly stable. On the hubitat side the hub slows down sometimes and drops the websocket connection but there is a watchdog that starts it back up.)

If some part of this integration does not work now or stops working in the future I make no gaurantees and this is provided "AS IS" without hope of service or warranty.  If you use this integration you agree to hold me unresponsible for what may happen to your Ring account in the event that Ring deems this type of usage of the API unreasonable.  You agree to hold me unresponsible for what may happen to your home, personal property, self, family, etc.  You agree to hold me unresponsible.  End of story.

If that sounds okay then continue onwards...

I don't expect this to be a perfect experience because I'm not providing a lot of direction and I don't have documented very well which drivers go to which devices beyond the names of the drivers and files. And they have A LOT of devices. 

Everyone should start by installing the app.  From there, there are two types of devices; devices that communicate via classic HTTP calls and devices that communicate via websockets.  It roughly breaks down like this:

- Security cameras, doorbells and chimes (classic HTTP devices)
- Beams devices (websocket devices)
- Security devices (websocket devices)

The app can interact directly with the non-websocket devices. The driver for the API device is required for all of the websocket devices. The dependency heirarchy will look like this a little:

                         App
             /                        \
        websocket                cameras/chimes/doorbells
          device
        /                \
     security          beams 
     devices          devices


Before you install any classic HTTP devices know that since we are not Ring partners we cannot get motion and ring notifications pushed to us.  Because of this I poll for them.  Yes, this is horrible and for that reason I do not poll myself.  (I have separate devices and I use the SmartThings integration with hublink).  However, I added this functionality because it seems to work for the home bridge project.  AND...  I know that I will spend forever explaining why I didn't add it if I don't.  Now I will probably just spend forever explaining why you can't poll more often for dings or dings are missed...  

I also added the ability for each light device to poll for its light status.  I also don't use this.  I use these devices for control.  I do not use them for status.  I don't ever need to know their status therefore I don't care what it is and I don't poll for status.

You do NOT need to install all of the device drivers in this repository.  You should be able to get away with installing the drivers for just the devices you own and have registered.  Here are the device to driver mappings roughly:

The app
- [unofficial-ring-connect.groovy](https://github.com/codahq/ring_hubitat_codahq/blob/master/src/apps/unofficial-ring-connect.groovy) - Required for all.  (does authentication and communication)

Children of the app
- [ring-api-virtual-device.groovy](https://github.com/codahq/ring_hubitat_codahq/blob/master/src/drivers/ring-api-virtual-device.groovy) - This is the "Ring API Virtual Device" or websocket device. Required if you have a Ring Alarm hub or a Ring Beams (Smart Lighting) bridge
- [ring-generic-chime.groovy](https://github.com/codahq/ring_hubitat_codahq/blob/master/src/drivers/ring-generic-chime.groovy) - Chime or Chime Pro
- [ring-generic-light-with-siren.groovy](https://github.com/codahq/ring_hubitat_codahq/blob/master/src/drivers/ring-generic-light-with-siren.groovy) - Floodlight Cam, Stickup Cam Wired, Spotlight Cam Wired
-  [ring-generic-light.groovy](https://github.com/codahq/ring_hubitat_codahq/blob/master/src/drivers/ring-generic-light.groovy) - Spotlight Cam Battery (A few devices where the siren call is different and I haven't reverse engineered it yet.)
- [ring-generic-camera-with-siren.groovy](https://github.com/codahq/ring_hubitat_codahq/blob/master/src/drivers/ring-generic-camera-with-siren.groovy) - Indoor Cam, Stick Up Cam
- [ring-generic-camera.groovy](https://github.com/codahq/ring_hubitat_codahq/blob/master/src/drivers/ring-generic-camera.groovy) - Doorbells

Children of the Ring API Virtual Device (websocket device)
- [ring-virtual-alarm-hub.groovy](https://github.com/codahq/ring_hubitat_codahq/blob/master/src/drivers/ring-virtual-alarm-hub.groovy) - Required if you have a Ring Alarm
- [ring-virtual-alarm-range-extender.groovy](https://github.com/codahq/ring_hubitat_codahq/blob/master/src/drivers/ring-virtual-alarm-range-extender.groovy) - Range Extender
- [ring-virtual-alarm-smoke-co-listener.groovy](https://github.com/codahq/ring_hubitat_codahq/blob/master/src/drivers/ring-virtual-alarm-smoke-co-listener.groovy) - Smoke/CO2 Listener
- [ring-virtual-contact-sensor.groovy](https://github.com/codahq/ring_hubitat_codahq/blob/master/src/drivers/ring-virtual-contact-sensor.groovy) - Ring Alarm Contact Sensor
- [ring-virtual-motion-sensor.groovy](https://github.com/codahq/ring_hubitat_codahq/blob/master/src/drivers/ring-virtual-motion-sensor.groovy) - Ring Alarm Motion Sensor
- [ring-virtual-keypad.groovy](https://github.com/codahq/ring_hubitat_codahq/blob/master/src/drivers/ring-virtual-keypad.groovy) - Ring Alarm Keypad
- [ring-virtual-lock.groovy](https://github.com/codahq/ring_hubitat_codahq/blob/master/src/drivers/ring-virtual-lock.groovy) - Any Z-Wave lock that connects to Ring Alarm (I think)
- [ring-virtual-beams-bridge.groovy](https://github.com/codahq/ring_hubitat_codahq/blob/master/src/drivers/ring-virtual-beams-bridge.groovy) - Smart Lighting (Beams) bridge.  Not required but it should keep the log quiet
- [ring-virtual-beams-group.groovy](https://github.com/codahq/ring_hubitat_codahq/blob/master/src/drivers/ring-virtual-beams-group.groovy) - Smart Lighting Group
- [ring-virtual-beams-light.groovy](https://github.com/codahq/ring_hubitat_codahq/blob/master/src/drivers/ring-virtual-beams-light.groovy) - Smart Lighting Light with Motion Sensor
- [ring-virtual-beams-motion-sensor.groovy](https://github.com/codahq/ring_hubitat_codahq/blob/master/src/drivers/ring-virtual-beams-motion-sensor.groovy) - Smart Lighting Motion Sensor

The app will create the camera, chime and doorbell devices automatically.  However, for testing reasons (and some level of control over what devices are installed) the security and beams devices are NOT created automatically.  Once you add the "Ring API Virtual Device" you must go and click install devices on your respective device to get its websocket children device(s) to create.  

IF YOU NEED SUPPORT DO NOT OPEN AN ISSUE ON GITHUB.  Issues are for code problems aka bugs.  If you have a support issue please make a post here in [this](https://community.hubitat.com/t/release-ring-integration/26423) thread.

The repository:
https://github.com/codahq/ring_hubitat_codahq

