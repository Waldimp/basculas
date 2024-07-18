'use strict';

var btnConnect = document.getElementById('connect');
var btnStop = document.getElementById('stop');
var valor_peso = document.getElementById('valor_peso');
var label_conectado = document.getElementById('label_conectado');
var available = document.getElementById('available');

var stompClient = null;
var username = null;

var colors = [
    '#2196F3', '#32c787', '#00BCD4', '#ff5652',
    '#ffc107', '#ff85af', '#FF9800', '#39bbb0'
];

let host = '';
let port = null;

function connect(event) {
    btnConnect.disabled = true;
    btnStop.disabled = false;

    var socket = new SockJS(`/ws`);
    stompClient = Stomp.over(socket);

    stompClient.connect({}, onConnected, onError);

    event.preventDefault();
}


function onConnected() {
    console.log(available.value);

    let partes = available.value.split(":");
    host = partes[0];
    port = partes[1];

    // Tell your username to the server
    stompClient.send("/app/tcp.addUser",
        {},
        JSON.stringify({sender: 'walter', type: 'JOIN', host: host, puerto: port})
    )

    // Subscribe to the Public Topic
    stompClient.subscribe('/user/topic/public', onMessageReceived);

    label_conectado.innerHTML = 'Conectado';
}


function onError(error) {
    label_conectado.textContent = 'Could not connect to WebSocket server. Please refresh this page to try again!';
    label_conectado.style.color = 'red';
}


function sendMessage(event) {
    var messageContent = messageInput.value.trim();
    if(messageContent && stompClient) {
        var Message = {
            sender: username,
            content: messageInput.value,
            type: 'CHAT'
        };
        stompClient.send("/app/tcp.sendMessage", {}, JSON.stringify(Message));
        messageInput.value = '';
    }
    event.preventDefault();
}


function onMessageReceived(payload) {
    var message = JSON.parse(payload.body);
    console.log("Mensaje recibido: ", message);

    valor_peso.innerHTML = message.content;

}

function disconnect() {

    btnConnect.disabled = false;
    btnStop.disabled = true;


    // Tell your username to the server
    stompClient.send("/app/tcp.stopConnection",
        {},
        JSON.stringify({host: host, puerto: port})
    )

    stompClient.disconnect({}, {});

    label_conectado.innerHTML = 'Desconectado';
}


//usernameForm.addEventListener('submit', connect, true)
// messageForm.addEventListener('submit', sendMessage, true)

btnConnect.addEventListener('click', connect, true);

btnStop.addEventListener('click', disconnect, true);


