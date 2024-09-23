'use strict';

var btnConnect = document.getElementById('connect');
var btnStop = document.getElementById('stop');
var valor_peso = document.getElementById('valor_peso');
var label_conectado = document.getElementById('label_conectado');
var available = document.getElementById('available');

var stompClient = null;
var username = null;

document.addEventListener('DOMContentLoaded', function () {
    cargarIPs();
});

function cargarIPs() {
    fetch('/ips')
        .then(response => response.json())
        .then(data => {
            const select = document.getElementById('available');
            select.innerHTML = ''; // Limpiar el select actual
            data.forEach(ip => {
                const option = document.createElement('option');
                option.value = ip;
                option.text = ip.replace(':', ' - ');
                select.appendChild(option);
            });
        })
        .catch(error => console.error('Error al cargar las IPs:', error));
}

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

document.getElementById('agregar_ip').addEventListener('click', function () {
    const nuevaIP = document.getElementById('nueva_ip').value;
    const nuevoPuerto = document.getElementById('nuevo_puerto').value;

    if (nuevaIP && nuevoPuerto) {
        const ipCompleta = `${nuevaIP}:${nuevoPuerto}`;

        fetch('/ips', {
            method: 'POST',
            headers: {
                'Content-Type': 'text/plain'  // Cambiar a 'text/plain'
            },
            body: ipCompleta  // No se necesita JSON.stringify aquí
        })
            .then(response => {
                if (response.ok) {
                    cargarIPs(); // Recargar la lista de IPs
                    document.getElementById('nueva_ip').value = ''; // Limpiar los campos
                    document.getElementById('nuevo_puerto').value = '';
                } else {
                    alert('Error al agregar la IP');
                }
            })
            .catch(error => alert('Error:' + error));
    } else {
        alert('Debe ingresar tanto la IP como el puerto');
    }
});