 /**
 *  Insteon Direct Dimmer/Switch
 *  Original Author     : ethomasii@gmail.com
 *  Creation Date       : 2013-12-08
 *
 *  Rewritten by        : idealerror
 *  Last Modified Date  : 2016-12-13 
 *
 *  Rewritten by        : kuestess
 *  Last Modified Date  : 2017-09-30
 * 
 *  Hubitat port by    @cwwilson08
 *  Last Modified Date  : 2018-09-09
 *
 *  Added Fast ON/OFF & Refresh setting in driver by     @cwwilson08   
 *  Last Modified Date  : 2018-09-14
 * 
 *  Changelog:
 * 
 *  2018-10-01: Added ability to disable auto refresh in driver
 *  2018-09-14: Added Fast ON/OFF & Refresh setting in driver
 *  2018-09-09: Initial release for Hubitat Elevation Hub
 *  2016-12-13: Added polling for Hub2
 *  2016-12-13: Added background refreshing every 3 minutes
 *  2016-11-21: Added refresh/polling functionality
 *  2016-10-15: Added full dimming functions
 *  2016-10-01: Redesigned interface tiles
 */

import groovy.json.JsonSlurper

metadata {
    definition (name: "Insteon direct dimmer/switch", namespace: "cw", author: "cwwilson08") {
        capability "Switch Level"
        capability "Switch"
        capability "Refresh"
    }
}

def fon = [:]
    fon << ["true" : "True"]
    fon << ["false" : "False"]



preferences {
    input("deviceid", "text", title: "Device ID", description: "Your Insteon device.  Do not include periods example: FF1122.")
    input("host", "text", title: "URL", description: "The URL of your Hub (without http:// example: my.hub.com ")
    input("port", "text", title: "Port", description: "The hub port.")
    input("username", "text", title: "Username", description: "The hub username (found in app)")
    input("password", "text", title: "Password", description: "The hub password (found in app)")
    input(name: "fastOn", type: "enum", title: "FastOn", options: fon, description: "Use FastOn/Off?", required: true)
} 
 



// Not in use
def parse(String description) {
}

def on() {
    log.debug "Turning device ON"
    sendEvent(name: "switch", value: "on");
    sendEvent(name: "level", value: 100, unit: "%")
    if (fastOn == "false") {
    	sendCmd("11", "FF")
    } else {
        sendCmd("12", "FF")
    }
}

def off() {
    log.debug "Turning device OFF"
    sendEvent(name: "switch", value: "off");
    sendEvent(name: "level", value: 0, unit: "%")
    if (fastOn == "false") {
    	sendCmd("13", "00")
    } else {
        sendCmd("14", "00")
    }
}
                
                
                  
def setLevel(value) {

    // log.debug "setLevel >> value: $value"
    
    // Max is 255
    def percent = value / 100
    def realval = percent * 255
    def valueaux = realval as Integer
    def level = Math.max(Math.min(valueaux, 255), 0)
    if (level > 0) {
        sendEvent(name: "switch", value: "on")
    } else {
        sendEvent(name: "switch", value: "off")
    }
    // log.debug "dimming value is $valueaux"
    log.debug "dimming to $level"
    dim(level,value)
}

def setLevel(value,rate) {

    // log.debug "setLevel >> value: $value"
    
    // Max is 255
    def percent = value / 100
    def realval = percent * 255
    def valueaux = realval as Integer
    def level = Math.max(Math.min(valueaux, 255), 0)
    if (level > 0) {
        sendEvent(name: "switch", value: "on")
    } else {
        sendEvent(name: "switch", value: "off")
    }
    // log.debug "dimming value is $valueaux"
    log.debug "dimming to $level"
    dim(level,value)
}

def dim(level, real) {
    String hexlevel = level.toString().format( '%02x', level.toInteger() )
    // log.debug "Dimming to hex $hexlevel"
    sendCmd("11",hexlevel)
    sendEvent(name: "level", value: real, unit: "%")
}

def sendCmd(num, level)
{
    log.debug "Sending Command"

    // Will re-test this later
    // sendHubCommand(new physicalgraph.device.HubAction("""GET /3?0262${settings.deviceid}0F${num}${level}=I=3 HTTP/1.1\r\nHOST: IP:PORT\r\nAuthorization: Basic B64STRING\r\n\r\n""", physicalgraph.device.Protocol.LAN, "${deviceNetworkId}"))
    httpGet("http://${settings.username}:${settings.password}@${settings.host}:${settings.port}//3?0262${settings.deviceid}0F${num}${level}=I=3") {response -> 
        def content = response.data
        
        // log.debug content
    }
    log.debug "Command Completed"
}

def refresh()
{
    log.debug "Refreshing.."
    getStatus()
}


def installed() {
	updated()
}

def updated() {}
       

def getStatus() {
	def params = [
        uri: "http://${settings.username}:${settings.password}@${settings.host}:${settings.port}/3?0262${settings.deviceid}0F1900=I=3"
    ]
    
    try {
        httpPost(params) {
            
            log.debug "commandsent"
        }
    } catch (e) {
        log.error "something went wrong: $e"
    }
    def buffer_status = runIn(2, getBufferStatus)
}

def getBufferStatus() {
    
    
	def buffer = ""
	def params = [
        uri: "http://${settings.username}:${settings.password}@${settings.host}:${settings.port}/buffstatus.xml"
    ]
    
    try {
        httpPost(params) {resp ->
            buffer = "${resp.responseData}"
            log.debug "Buffer: ${resp.responseData}"
        }
    } catch (e) {
        log.error "something went wrong: $e"
    }

	def buffer_end = buffer.substring(buffer.length()-2,buffer.length())
	def buffer_end_int = Integer.parseInt(buffer_end, 16)
    
    def parsed_buffer = buffer.substring(0,buffer_end_int)
    log.debug "ParsedBuffer: ${parsed_buffer}"
    
    def responseID = parsed_buffer.substring(22,28)
    
if (responseID == settings.deviceid) {
            log.debug "Response is for correct device: ${responseID}"
            def status = parsed_buffer.substring(38,40)
            log.debug "Status: ${status}"
    		
            def level = Math.round(Integer.parseInt(status, 16)*(100/255))
            log.debug "Level: ${level}"
            
            if (level == 0) {
                log.debug "Device is off..."
                sendEvent(name: "switch", value: "off")
                sendEvent(name: "level", value: level, unit: "%")
                
                }

            else if (level > 0) {
                log.debug "Device is on..."
                sendEvent(name: "switch", value: "on")
                sendEvent(name: "level", value: level, unit: "%")
                
            }
        } else {
        	log.debug "Response is for wrong device - trying again"
            getStatus()
        }
    }
