package com.wind;

import java.io.IOException;
import java.io.DataInputStream;
import java.io.DataOutputStream;

import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import com.wind.ProxyHandler.ProxyHandlerImp;
import com.wind.interfaces.ProxyHandlerInterface;


class LocationServer {
    private static final int PORT = 4000;
    private static final int RMI_PORT = 1099;
    private static final String RMI_NAME = "ProxyHandler";

    private static ProxyHandlerInterface proxyHandler;

    public static void main(String[] args) {
        try {
            // Start the RMI service
            proxyHandler = new ProxyHandlerImp();
            Registry registry = LocateRegistry.createRegistry(RMI_PORT);
            registry.rebind(RMI_NAME, proxyHandler);

            System.out.println("RMI Service registered on port " + RMI_PORT + "\n");
            
            // Start the socket server
            try (ServerSocket serverSocket = new ServerSocket(PORT)) {
                System.out.println("\r\n" + //
                                    "===========================================================\r\n" + //
                                    "                  _       ______      ____                 \r\n" + //
                                    "                 | |     / /  _/___  / __ \\               \r\n" + //
                                    "                 | | /| / // // __ \\/ / / /               \r\n" + //
                                    "                 | |/ |/ // // / / / /_/ /                 \r\n" + //
                                    "                 |__/|__/___/_/ /_/_____/                  \r\n" + //
                                    "                                                           \r\n" + //
                                    "          _                     _   _                      \r\n" + //
                                    "         | |                   | | (_)                     \r\n" + //
                                    "         | |     ___   ___ __ _| |_ _  ___  _ __           \r\n" + //
                                    "         | |    / _ \\ / __/ _` | __| |/ _ \\| '_ \\       \r\n" + //
                                    "         | |___| (_) | (_| (_| | |_| | (_) | | | |         \r\n" + //
                                    "         |______\\___/ \\___\\__,_|\\__|_|\\___/|_| |_|    \r\n" + //
                                    "                                                           \r\n" + //
                                    "===========================================================\r\n");
    
                System.out.println("Location Server started on port " + PORT + "\n");
                System.out.println("Location server up and running!\n");
    
                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("\n----------------------------------------------------------");
                    System.out.println("Client connected from " + clientSocket.getInetAddress().getHostAddress());
    
                    Thread clientThread = new Thread(new ClientHandler(clientSocket, proxyHandler));
                    clientThread.start();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}


class ClientHandler implements Runnable {
    private Socket clientSocket;
    private ProxyHandlerInterface proxyHandler;

    public ClientHandler(Socket clientSocket, ProxyHandlerInterface proxyHandler) {
        this.clientSocket = clientSocket;
        this.proxyHandler = proxyHandler;
    }

    public void run() {
        try {
            DataInputStream in = new DataInputStream(clientSocket.getInputStream());
            DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());

            String message = in.readUTF();
            if (message.equals("eW91IHNoYWxsIG5vdCBwYXNz")) {
                System.out.println("   -> Station key is valid. Sending proxy location...");

                // Send proxy to the station
                try {
                    out.writeUTF(proxyHandler.getNextProxyAddress());
                    out.writeInt(proxyHandler.getNextProxyPort());
                } catch (RemoteException e) {
                    System.out.println("   -> WARNING! No proxies available");
                    out.writeUTF("");
                    out.writeInt(-1);
                }
                
            } else if (message.equals("bWF5IHRoZSBmb3JjZSBiZSB3aXRoIHlvdQ==")) {
                System.out.println("   -> Client key is valid. Sending proxy location...");

                // Send proxy to the client
                try {
                    out.writeUTF(proxyHandler.getMulticastAddress());
                    out.writeInt(proxyHandler.getMulticastPort());
                } catch (RemoteException e) {
                    System.out.println("   -> WARNING! No proxies available");
                    out.writeUTF("");
                    out.writeInt(-1);
                }
            }
            
            else {
                System.out.println("   -> WARNING! Client's key is not recognized: " + message);
            }

            clientSocket.close();
            System.out.println("----------------------------------------------------------");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}