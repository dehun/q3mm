function queueWsConnect() {
var socket;

try{
var socket = new WebSocket(wshost);

 socket.onopen = function(){
    alert("ws opened");
 }

 socket.onmessage = function(msg){
    alert("ws received" + JSON.stringify(msg.data));
 }

 socket.onclose = function(){
     alert("ws closed");
 }

 } catch(exception){
    alert("ws exception" + exception);
 }
}


$(document).ready(function() {
   if (typeof userName != 'undefined') {
       queueWsConnect()
   }
})
