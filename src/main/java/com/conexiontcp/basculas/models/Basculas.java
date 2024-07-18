package com.conexiontcp.basculas.models;

import lombok.Getter;
import lombok.Setter;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;


public class Basculas {
    public Socket socket;
    public String host;
    public int puerto;
    public boolean connected;
    public boolean enviado;
    public InputStream input;
    public OutputStream output;
    public List<String> usuarios = new ArrayList<>();

    public Basculas(Socket client, String host, int port, boolean connected, String usuarioId) {
        this.socket = client;
        this.host = host;
        this.puerto = port;
        this.connected = connected;
        this.usuarios.add(usuarioId);
        this.enviado = false;
    }

    // Método para eliminar un usuario de la lista
    public boolean removeUsuario(String usuarioId) {
        return this.usuarios.remove(usuarioId);
    }

}
