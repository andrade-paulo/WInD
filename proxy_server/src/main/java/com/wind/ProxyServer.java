// ProxyServer.java - Refatorado para Sockets TCP com Clientes
package com.wind;

import com.wind.CO.ApplicationServerHandler;
import com.wind.CO.ClientHandler;
import com.wind.interfaces.ProxyHandlerInterface;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

public class ProxyServer {
    private static final int PROXY_CLIENT_PORT = 12345;
    
    // ApplicationServer
    private static final String MAIN_SERVER_HOST = "localhost";
    private static final int MAIN_SERVER_PORT = 54321;
    
    private static final String LOCATION_SERVER_RMI_HOST = "localhost";
    private static final int LOCATION_SERVER_RMI_PORT = 1099;
    private static final String LOCATION_SERVER_RMI_NAME = "ProxyHandler";
    private static final int PROXY_HEARTBEAT_PORT = 12346;
    private static final int PROXY_RMI_REPLICA_PORT = 0;
    
    private static final List<ClientHandler> connectedClients = new CopyOnWriteArrayList<>();
    public static ApplicationServerHandler applicationServerCommunicator = null;

    public static void main(String[] args) {
        System.out.println("\r\n" + //
                    "===========================================================\r\n" + //
                    "                  _       ______      ____                 \r\n" + //
                    "                 | |     / /  _/___  / __ \\               \r\n" + //
                    "                 | | /| / // // __ \\/ / / /               \r\n" + //
                    "                 | |/ |/ // // / / / /_/ /                 \r\n" + //
                    "                 |__/|__/___/_/ /_/_____/                  \r\n" + //
                    "                                                           \r\n" + //
                    "      ___                    ___                           \r\n" + //
                    "     | _ \\_ _ _____ ___  _  / __| ___ _ ___ _____ _ _     \r\n" + //
                    "     |  _/ '_/ _ \\ \\ / || | \\__ \\/ -_) '_\\ V / -_) '_|\r\n" + //
                    "     |_| |_| \\___/_\\_\\\\_, | |___/\\___|_|  \\_/\\___|_|\r\n" + //
                    "                      |__/                                 \r\n" + //
                    "                                                           \r\n" + //
                    "===========================================================\r\n");
        
        HeartbeatResponder heartbeatResponder = null;
        ProxyHandlerInterface locationService = null;
        ServerSocket clientServerSocket = null;

        final CountDownLatch latch = new CountDownLatch(1);

        String proxyHostAddress;
        try {
            proxyHostAddress = InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            System.err.println("[ProxyServer] Error getting local host address: " + e.getMessage());
            proxyHostAddress = "127.0.0.1";
        }
        final String finalProxyHostAddress = proxyHostAddress;

        try {
            // Lógica de Heartbeat e RMI com LocationServer
            heartbeatResponder = new HeartbeatResponder(PROXY_HEARTBEAT_PORT);
            new Thread(heartbeatResponder::startListening, "HeartbeatResponderThread").start();
            System.out.println("[ProxyServer] Heartbeat responder started on port " + PROXY_HEARTBEAT_PORT);

            try {
                Registry registry = LocateRegistry.getRegistry(LOCATION_SERVER_RMI_HOST, LOCATION_SERVER_RMI_PORT);
                locationService = (ProxyHandlerInterface) registry.lookup(LOCATION_SERVER_RMI_NAME);

                // Informa a porta onde os clientes devem se conectar
                locationService.registerProxy(finalProxyHostAddress, PROXY_CLIENT_PORT, PROXY_HEARTBEAT_PORT, PROXY_RMI_REPLICA_PORT, "N/A", 0);
                System.out.println("[ProxyServer] Successfully registered with LocationServer at " +
                                   LOCATION_SERVER_RMI_HOST + ":" + LOCATION_SERVER_RMI_PORT);
            } catch (Exception e) {
                System.err.println("[ProxyServer] Error connecting to or registering with LocationServer RMI: " + e.getMessage());
            }
            
            // O callback agora chama o método para broadcast via TCP
            applicationServerCommunicator = new ApplicationServerHandler(
                    MAIN_SERVER_HOST,
                    MAIN_SERVER_PORT
            );

            applicationServerCommunicator.connect();

            // Inicialização do servidor de clientes
            clientServerSocket = new ServerSocket(PROXY_CLIENT_PORT);
            System.out.println("[ProxyServer] Listening for clients on port " + PROXY_CLIENT_PORT);

            ServerSocket finalClientServerSocket = clientServerSocket;

            Thread clientListenerThread = new Thread(() -> {
                while (!finalClientServerSocket.isClosed()) {
                    try {
                        Socket clientSocket = finalClientServerSocket.accept();
                        System.out.println("[ProxyServer] New client connected: " + clientSocket.getRemoteSocketAddress());

                        // ClientHandler gerencia a conexão do cliente
                        ClientHandler handler = new ClientHandler(clientSocket);
                        connectedClients.add(handler);
                        new Thread(handler).start();
                    } catch (IOException e) {
                        if (finalClientServerSocket.isClosed()) {
                            System.out.println("[ProxyServer] Client listener socket closed.");
                        } else {
                            System.err.println("[ProxyServer] Error accepting client connection: " + e.getMessage());
                        }
                    }
                }
            }, "ClientListenerThread");
            clientListenerThread.start();
            
            System.out.println("[ProxyServer] All components initialized. Proxy is running.");
            System.out.println("Ctrl+C to stop.");
            
            // Lógica de Shutdown
            final ApplicationServerHandler finalMainComm = applicationServerCommunicator;
            final HeartbeatResponder finalHeartbeatResponder = heartbeatResponder;
            final ServerSocket finalClientSocketForShutdown = clientServerSocket;

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("[ProxyServer] Shutdown initiated...");
                // Desconecta do RMI
                if (finalHeartbeatResponder != null) finalHeartbeatResponder.stopListening();
                if (finalMainComm != null) finalMainComm.disconnect();
                try {
                    if (finalClientSocketForShutdown != null) finalClientSocketForShutdown.close();
                } catch (IOException e) { /*...*/ }

                // Fecha a conexão com todos os clientes
                for(ClientHandler handler : connectedClients) {
                    handler.closeConnection();
                    connectedClients.remove(handler);
                }

                System.out.println("[ProxyServer] All components shut down.");
                latch.countDown();
            }, "ShutdownHookThread"));

            latch.await();

        } catch (IOException | InterruptedException e) {
            System.err.println("[ProxyServer] Critical error in main thread: " + e.getMessage());
            e.printStackTrace();
        } 
    }
}