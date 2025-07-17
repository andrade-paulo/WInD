// ApplicationServerHandler.java - Refatorado para Requisição-Resposta
package com.wind.CO;

import com.wind.message.Message;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;

public class ApplicationServerHandler {
    private final String appServerHost;
    private final int appServerPort;

    private Socket socket;
    private ObjectOutputStream oos;
    private ObjectInputStream ois;
    
    private volatile boolean connected = false;

    public ApplicationServerHandler(String host, int port) {
        this.appServerHost = host;
        this.appServerPort = port;
    }

    public void connect() throws IOException {
        this.socket = new Socket(appServerHost, appServerPort);
        
        this.oos = new ObjectOutputStream(socket.getOutputStream());
        this.oos.flush();
        this.ois = new ObjectInputStream(socket.getInputStream());
        
        connected = true;
        System.out.println("[Proxy] Connected to Application Server at " + appServerHost + ":" + appServerPort);
    }

    public synchronized Message sendRequest(Message request) throws IOException, ClassNotFoundException {
        if (!connected || socket.isClosed() || oos == null || ois == null) {
            throw new IOException("Not connected to the Application Server.");
        }

        try {
            // Envia o objeto de requisição
            oos.writeObject(request);
            oos.flush();
            oos.reset(); 

            return (Message) ois.readObject();

        } catch (SocketException e) {
            System.err.println("[Proxy] Connection to Application Server lost during request: " + e.getMessage());
            disconnect();
            throw e; // Relança a exceção para que o caller saiba que a conexão caiu.
        }
    }


    public void disconnect() {
        if (!connected) return;

        connected = false;
        System.out.println("[Proxy] Disconnecting from Application Server.");
        
        try {
            if (ois != null) ois.close();
            if (oos != null) oos.close();
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            System.err.println("[Proxy] Error during disconnection from Application Server: " + e.getMessage());
        }
    }
}