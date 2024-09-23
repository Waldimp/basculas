package com.conexiontcp.basculas.conexion;

import com.conexiontcp.basculas.models.*;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.security.Principal;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@RestController
public class ConexionController {
    private static List<Basculas> clientes = new CopyOnWriteArrayList<>();
    private static List<String> listaIPs = new CopyOnWriteArrayList<>();
    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    private ScheduledExecutorService executorService = Executors.newScheduledThreadPool(10); // 10 hilos

    public ConexionController(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @PostConstruct
    public void startSendingMessages() {
        executorService.scheduleAtFixedRate(this::llamado, 0, 1, TimeUnit.SECONDS);
    }

    static {
        // Lista inicial de direcciones IP y puertos
        listaIPs.add("192.168.2.1:5004");
        listaIPs.add("127.0.0.1:5001");
        listaIPs.add("192.168.2.1:5005");
    }

    private void llamado(){
        for (Basculas cliente : clientes) {
            executorService.scheduleAtFixedRate(() -> this.sendMessagesBascula(cliente),
                    0,
                    10,
                    TimeUnit.MILLISECONDS);
        }
    }

    private void sendMessagesBascula(Basculas cliente) {
        try {
                if (cliente.connected) {
                    try {
                        OutputStream output = cliente.socket.getOutputStream();
                        cliente.output = output; // Asegurar que los streams estén asignados una sola vez

                        if(cliente.enviado == false){
                            // Enviar mensaje al servidor
                            enviarMensaje(cliente, "¡Hola mundo desde el cliente!");
                            cliente.enviado = true;
                        }

                        InputStream input = cliente.socket.getInputStream();
                        cliente.input = input;

                        if(!cliente.socket.isClosed() && cliente.connected){
                            String mensajex = leerMensaje(cliente);

                            if (mensajex != null) {
                                System.out.println("Mensaje desde for: " + mensajex);

                                // Simular la lógica del chat
                                Message chatMessage = new Message();
                                chatMessage.setContent(mensajex);
                                chatMessage.setPuerto(cliente.puerto);
                                chatMessage.setHost(cliente.host);

                                // Enviar el mensaje de vuelta si es necesario
                                for (String usuarioId : cliente.usuarios) {
                                    messagingTemplate.convertAndSendToUser(usuarioId,"/topic/public", chatMessage);
                                }

                            }
                        }


                    } catch (IOException e) {
                        System.out.println("ERROR IOException: " + e.getMessage());
                        cliente.connected = false;
                    }
                } else {
                    cliente.socket.close();
                }
        } catch (Exception e) {
            cliente.connected = false;
            System.out.println("ERROR Exception: " + e.getMessage());
        }

    }

    private static String leerMensaje(Basculas cliente) throws IOException {
        try{
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int nRead;
            byte[] data = new byte[1024];
            if(!cliente.socket.isClosed()){
                // Leer datos hasta que se reciba el final del mensaje
                while ((nRead = cliente.input.read(data, 0, data.length)) != -1) {
                    buffer.write(data, 0, nRead);
                    if (nRead < data.length) {
                        // Hasta que hemos leído todo el mensaje
                        break;
                    }
                }

                buffer.flush();
            }

            return new String(buffer.toByteArray());
        } catch (SocketException e) {
            System.out.println("ERROR EN EL socket, probablemente desconectado: " + e.getMessage());
            cliente.connected = false;
            return null;
        } catch (Exception e) {
            System.out.println("Excepción: " + e.getMessage());
            cliente.connected = false;
            return null;
        }

    }

    private static void enviarMensaje(Basculas cliente, String mensaje) throws IOException {
        byte[] datos = mensaje.getBytes();
        cliente.output.write(datos);
        cliente.output.flush();
    }

    @MessageMapping("/tcp.sendMessage")
    @SendToUser("/topic/public")
    public Message sendMessage(
            @Payload Message chatMessage, Principal principal
    ) throws Exception {
        return chatMessage;
    }

    @MessageMapping("/tcp.addUser")
    @SendToUser("/topic/public")
    public Message addUser(
            @Payload Message chatMessage,
            SimpMessageHeaderAccessor headerAccessor,
            Principal principal
    ) throws IOException {

        Basculas existingClient = findCliente(clientes, chatMessage.getHost(), chatMessage.getPuerto());

        if (existingClient != null) {
            existingClient.usuarios.add(principal.getName());
        } else {
            Basculas nuevo = new Basculas(
                    new Socket(chatMessage.getHost(), chatMessage.getPuerto()),
                    chatMessage.getHost(),
                    chatMessage.getPuerto(),
                    true,
                    principal.getName()
            );
            clientes.add(nuevo);
        }

        // Add username in web socket session
        headerAccessor.getSessionAttributes().put("username", chatMessage.getSender());
        return chatMessage;
    }

    // Método para verificar si ya existe un cliente con el mismo host y puerto
    public static boolean clienteExists(List<Basculas> clientes, String host, int port) {
        return clientes.stream().anyMatch(cliente -> cliente.host.equals(host) && cliente.puerto == port);
    }

    // Método para encontrar un cliente con el mismo host y puerto
    public static Basculas findCliente(List<Basculas> clientes, String host, int port) {
        return clientes.stream()
                .filter(cliente -> cliente.host.equals(host) && cliente.puerto == port)
                .findFirst()
                .orElse(null);
    }

    @MessageMapping("/tcp.stopConnection")
    @SendTo("/topic/public")
    public boolean stopConnection(
            @Payload Message chatMessage,
            Principal principal
    ) throws IOException {

        try {
            System.out.println(principal.getName());
            System.out.println(chatMessage.getHost());

            Basculas existingClient = findCliente(clientes, chatMessage.getHost(), chatMessage.getPuerto());

            if (existingClient != null) {
                existingClient.removeUsuario(principal.getName());

                // Si la lista de usuarios está vacía, puedes decidir si quieres eliminar la báscula de la lista de clientes
                if (existingClient.usuarios.isEmpty()) {
                    existingClient.connected = false;
                    //existingClient.socket.close();
                    clientes.remove(existingClient);
                }
            }

            return true;
        }  catch (Exception e) {
            System.out.println("ERROR AL DETENER CONEXION: " + e.getMessage());
            return false;
        }
    }

    @GetMapping("/ips")
    public List<String> getIPs() {
        return listaIPs;
    }

    @PostMapping("/ips")
    public ResponseEntity<String> addIP(@RequestBody String nuevaIP) {
        listaIPs.add(nuevaIP);
        return ResponseEntity.ok("IP añadida");
    }

    @DeleteMapping("/ips")
    public ResponseEntity<String> removeIP(@RequestBody String ipToRemove) {
        listaIPs.remove(ipToRemove);
        return ResponseEntity.ok("IP eliminada");
    }

}
