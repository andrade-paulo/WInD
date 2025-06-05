package com.wind;

import com.wind.CO.ApplicationServerHandler;
import com.wind.CO.ClientMulticastEmitter;
import com.wind.CO.StationConnectionHandler;
import com.wind.interfaces.ProxyHandlerInterface;
import com.wind.model.DAO.WeatherDataDAO;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.net.InetAddress;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;

public class ProxyServer {
    private static final int PROXY_STATION_PORT = 12345;

    private static final String MAIN_SERVER_HOST = "localhost";
    private static final int MAIN_SERVER_PORT = 54321;

    private static final String MULTICAST_ADDRESS = "230.0.0.1";
    private static final int MULTICAST_PORT = 19000;

    private static final String LOCATION_SERVER_RMI_HOST = "localhost";
    private static final int LOCATION_SERVER_RMI_PORT = 1099;
    private static final String LOCATION_SERVER_RMI_NAME = "ProxyHandler";

    private static final int PROXY_HEARTBEAT_PORT = 12346;
    // A rmiReplicaPort pode ser 0 se não for usada ativamente pelo proxy
    private static final int PROXY_RMI_REPLICA_PORT = 0;


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


        ClientMulticastEmitter multicastEmitter = null;
        ApplicationServerHandler applicationServerCommunicator = null;
        StationConnectionHandler stationConnectionHandler = null;

        HeartbeatResponder heartbeatResponder = null;
        ProxyHandlerInterface locationService = null;

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
            heartbeatResponder = new HeartbeatResponder(PROXY_HEARTBEAT_PORT);
            new Thread(heartbeatResponder::startListening, "HeartbeatResponderThread").start();
            System.out.println("[ProxyServer] Heartbeat responder started on port " + PROXY_HEARTBEAT_PORT);

            try {
                Registry registry = LocateRegistry.getRegistry(LOCATION_SERVER_RMI_HOST, LOCATION_SERVER_RMI_PORT);
                locationService = (ProxyHandlerInterface) registry.lookup(LOCATION_SERVER_RMI_NAME);
                locationService.registerProxy(finalProxyHostAddress, PROXY_STATION_PORT, PROXY_HEARTBEAT_PORT, PROXY_RMI_REPLICA_PORT, MULTICAST_ADDRESS, MULTICAST_PORT); //
                System.out.println("[ProxyServer] Successfully registered with LocationServer at " +
                                   LOCATION_SERVER_RMI_HOST + ":" + LOCATION_SERVER_RMI_PORT +
                                   " as " + finalProxyHostAddress + ":" + PROXY_STATION_PORT +
                                   " (HB Port: " + PROXY_HEARTBEAT_PORT + ")");
            } catch (Exception e) {
                System.err.println("[ProxyServer] Error connecting to or registering with LocationServer RMI: " + e.getMessage());
                // Obs.: O proxy continua funcionando mesmo sem o LocationServer, mas não será descoberto.
            }

            multicastEmitter = new ClientMulticastEmitter(MULTICAST_ADDRESS, MULTICAST_PORT);
            final ClientMulticastEmitter finalMulticastEmitter = multicastEmitter;

            applicationServerCommunicator = new ApplicationServerHandler(
                    MAIN_SERVER_HOST,
                    MAIN_SERVER_PORT,
                    finalMulticastEmitter::multicastData
            );
            applicationServerCommunicator.connect();

            WeatherDataDAO weatherDataDAO = new WeatherDataDAO(applicationServerCommunicator);
            stationConnectionHandler = new StationConnectionHandler(PROXY_STATION_PORT, weatherDataDAO);

            Thread stationListenerThread = new Thread(stationConnectionHandler::startListening, "StationListenerThread");
            stationListenerThread.start();

            System.out.println("[ProxyServer] Weather data components initialized. Proxy is running.");
            System.out.println("Ctrl+C to stop.");


            final StationConnectionHandler finalStationHandler = stationConnectionHandler;
            final ApplicationServerHandler finalMainComm = applicationServerCommunicator;
            final HeartbeatResponder finalHeartbeatResponder = heartbeatResponder;
            final ProxyHandlerInterface finalLocationService = locationService; // Para desregistro

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("[ProxyServer] Shutdown initiated...");

                if (finalLocationService != null) {
                    try {
                        // TODO: Implementar um método unregisterProxy() na ProxyHandlerInterface 
                        // finalLocationService.unregisterProxy(finalProxyHostAddress, PROXY_STATION_PORT, PROXY_HEARTBEAT_PORT, PROXY_RMI_REPLICA_PORT);
                        // System.out.println("[ProxyServer] Unregistered from LocationServer.");
                        System.out.println("[ProxyServer] Unregister functionality not explicitly called (method missing in interface). LocationServer relies on heartbeat.");
                    } catch (Exception e) {
                        System.err.println("[ProxyServer] Error unregistering from LocationServer: " + e.getMessage());
                    }
                }

                if (finalHeartbeatResponder != null) finalHeartbeatResponder.stopListening();
                if (finalStationHandler != null) finalStationHandler.stopListening();
                if (finalMainComm != null) finalMainComm.disconnect();
                if (finalMulticastEmitter != null) finalMulticastEmitter.close();
                System.out.println("[ProxyServer] All components shut down.");
                latch.countDown();
            }, "ShutdownHookThread"));

            latch.await();

        } catch (IOException e) { // IOException do multicastEmitter ou mainServerComm.connect
            System.err.println("[ProxyServer] Failed to start proxy components: " + e.getMessage());
            e.printStackTrace();
        } catch (InterruptedException e) {
            System.err.println("[ProxyServer] Main thread interrupted.");
            Thread.currentThread().interrupt();
        } finally {
            System.out.println("[ProxyServer] Main method finally block executing.");
             if (latch.getCount() > 0) {
                if (heartbeatResponder != null) heartbeatResponder.stopListening();
                if (stationConnectionHandler != null) stationConnectionHandler.stopListening();
                if (applicationServerCommunicator != null) applicationServerCommunicator.disconnect();
                if (multicastEmitter != null) multicastEmitter.close();
            }
        }
    }
}