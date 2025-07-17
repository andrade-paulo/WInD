package com.wind.CO;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

import com.wind.ProxyServer;
import com.wind.message.Message;

public class ClientHandler implements Runnable { // Agora implementa Runnable
    private final Socket socket;
    private ObjectOutputStream oos;
    private ObjectInputStream ois;
    private final String clientAddress;
    private volatile boolean isRunning = true;

    public ClientHandler(Socket socket) {
        this.socket = socket;
        this.clientAddress = socket.getRemoteSocketAddress().toString();
    }
    
    @Override
    public void run() {
        try {
            this.oos = new ObjectOutputStream(socket.getOutputStream());
            this.ois = new ObjectInputStream(socket.getInputStream());

            // Loop para ouvir as requisições do cliente
            while (isRunning && !socket.isClosed()) {
                // Recebe a requisição do cliente
                Message clientRequest = (Message) ois.readObject();
                System.out.println("[Proxy] Received request " + clientRequest.getInstrucao() + " from client " + this.clientAddress);

                // Envia ao application server
                Message serverResponse = ProxyServer.applicationServerCommunicator.sendRequest(clientRequest);

                // Envia a resposta de volta para o cliente que fez a requisição
                System.out.println("[RESPONSE] Enviando resposta para o cliente");
                sendMessage(serverResponse);
            }

        } catch (IOException | ClassNotFoundException e) {
            if (e.getMessage() != null && e.getMessage().contains("Connection reset") || e instanceof java.io.EOFException) {
                System.out.println("[Proxy] Client " + this.clientAddress + " disconnected.");
            } else {
                System.err.println("[Proxy] Error with client " + this.clientAddress + ": " + e.getMessage());
            }
        } finally {
            closeConnection();
        }
    }

    public void sendMessage(Message message) {
        if (socket.isConnected() && oos != null) {
            try {
                oos.writeObject(message);
                oos.flush();
            } catch (IOException e) {
                System.err.println("Failed to send message to client " + clientAddress + ". Error: " + e.getMessage());
                closeConnection();
            }
        }
    }
    
    public void closeConnection() {
        isRunning = false;

        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) { /*...*/}
    }
}