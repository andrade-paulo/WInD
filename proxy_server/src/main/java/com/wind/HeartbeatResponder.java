package com.wind;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.List;

public class HeartbeatResponder {
    private final int heartbeatPort;
    private volatile boolean running = true;
    private ServerSocket serverSocket;

    public HeartbeatResponder(int port) {
        this.heartbeatPort = port;
    }

    public void startListening() {
        try {
            serverSocket = new ServerSocket(heartbeatPort);
            System.out.println("[HeartbeatResponder] Listening for heartbeats on port " + heartbeatPort);
            while (running) {
                Socket lsSocket = serverSocket.accept();
                try (ObjectInputStream ois = new ObjectInputStream(lsSocket.getInputStream())) {
                    Object receivedObject = ois.readObject();
                    if (receivedObject instanceof List) {
                        System.out.println("[HeartbeatResponder] Received proxy list from LocationServer during heartbeat.");
                        // List<ProxyEntity> proxyList = (List<ProxyEntity>) receivedObject;
                    } else {
                        System.out.println("[HeartbeatResponder] Received an unexpected object during heartbeat.");
                    }
                } catch (IOException | ClassNotFoundException e) {
                    System.err.println("[HeartbeatResponder] Error during heartbeat interaction: " + e.getMessage());
                } finally {
                    try {
                        lsSocket.close();
                    } catch (IOException e) { 
                        System.err.println("[HeartbeatResponder] Error closing socket: " + e.getMessage());
                    }
                }
            }
        } catch (SocketException e) {
            if (running) {
                 System.out.println("[HeartbeatResponder] SocketException (likely closed): " + e.getMessage());
            }
        } catch (IOException e) {
            if (running) {
                System.err.println("[HeartbeatResponder] IOException: " + e.getMessage());
            }
        } finally {
             System.out.println("[HeartbeatResponder] Stopped listening.");
        }
    }

    public void stopListening() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("[HeartbeatResponder] Error closing server socket: " + e.getMessage());
        }
    }
}