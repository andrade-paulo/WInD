package com.wind;

import com.wind.entities.WeatherData;
import com.wind.entities.MicrocontrollerEntity;
import com.wind.message.Message;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;


public class WeatherStation {
    private String locationServerHost;
    private int locationServerPort;
    private static final String LOCATION_SERVER_AUTH_KEY = "eW91IHNoYWxsIG5vdCBwYXNz";

    private Socket proxySocket;
    private ObjectOutputStream proxyObjectOutStream;
    private ObjectInputStream proxyObjectInStream;

    private static final int MC_LISTENER_PORT = 19002;
    private ServerSocket mcServerSocket;
    private Thread mcAcceptorThread;
    private final ExecutorService mcConnectionHandlerExecutor = Executors.newCachedThreadPool();
    private static final AtomicInteger mcHandlerThreadId = new AtomicInteger(0);

    private volatile boolean isRunning = true;
    private final ExecutorService proxyForwarderExecutor = Executors.newSingleThreadExecutor();


    private static class ProxyServerInfo {
        final String host;
        final int port;
        ProxyServerInfo(String host, int port) { this.host = host; this.port = port; }
        boolean isValid() { return host != null && !host.isEmpty() && port > 0; }
        @Override public String toString() { return host + ":" + port; }
    }

    public WeatherStation(String locationServerHost, int locationServerPort) {
        this.locationServerHost = locationServerHost;
        this.locationServerPort = locationServerPort;
    }


    public static void main(String[] args) {
        System.out.println("\r\n" + //
                    "===========================================================\r\n" + //
                    "                  _       ______      ____                 \r\n" + //
                    "                 | |     / /  _/___  / __ \\               \r\n" + //
                    "                 | | /| / // // __ \\/ / / /               \r\n" + //
                    "                 | |/ |/ // // / / / /_/ /                 \r\n" + //
                    "                 |__/|__/___/_/ /_/_____/                  \r\n" + //
                    "                                                           \r\n" + //
                    "             _____ _        _   _                          \r\n" + //
                    "            / ____| |      | | (_)                         \r\n" + //
                    "           | (___ | |_ __ _| |_ _  ___  _ __               \r\n" + //
                    "            \\___ \\| __/ _` | __| |/ _ \\| '_ \\          \r\n" + //
                    "            ____) | || (_| | |_| | (_) | | | |             \r\n" + //
                    "           |_____/ \\__\\__,_|\\__|_|\\___/|_| |_|         \r\n" + //
                    "                                                           \r\n" + //
                    "===========================================================\r\n");


        String locServerHost = "localhost";
        int locServerPort = 4000;

        if (args.length >= 1) {
            locServerHost = args[0];
        }
        if (args.length >= 2) {
            try {
                locServerPort = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port for LocationServer: " + args[1] + ". Using default " + locServerPort);
            }
        }
        System.out.println("[" + new Date() + " Main] WeatherStation (Unicast MC Listener) starting. LocationServer: " + locServerHost + ":" + locServerPort);

        WeatherStation station = new WeatherStation(locServerHost, locServerPort);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[" + new Date() + " Main] Shutdown hook triggered for WeatherStation.");
            station.stop();
        }, "WeatherStation-ShutdownHook"));

        station.start();
    }


    public void start() {
        System.out.println("[" + new Date() + " WeatherStation] Initializing...");
        startMicrocontrollerAcceptor();
        System.out.println("[" + new Date() + " WeatherStation] Running. Accepting MC connections and forwarding data to Proxy.");
    }

    public void stop() {
        System.out.println("[" + new Date() + " WeatherStation] Stopping...");
        this.isRunning = false;

        if (mcAcceptorThread != null) {
            mcAcceptorThread.interrupt();
        }
        try {
            if (mcServerSocket != null && !mcServerSocket.isClosed()) {
                mcServerSocket.close();
            }
        } catch (IOException e) {
            System.err.println("[" + new Date() + " WeatherStation] Error closing MC ServerSocket: " + e.getMessage());
        }
        
        mcConnectionHandlerExecutor.shutdownNow();
        try {
            if (!mcConnectionHandlerExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                System.err.println("[" + new Date() + " WeatherStation] MC connection handler executor did not terminate.");
            }
        } catch (InterruptedException e) {
            mcConnectionHandlerExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        proxyForwarderExecutor.shutdownNow();
         try {
            if (!proxyForwarderExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                System.err.println("[" + new Date() + " WeatherStation] Proxy forwarder executor did not terminate.");
            }
        } catch (InterruptedException e) {
            proxyForwarderExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        disconnectFromProxy();
        System.out.println("[" + new Date() + " WeatherStation] Stopped.");
    }


    private void startMicrocontrollerAcceptor() {
        mcAcceptorThread = new Thread(() -> {
            try {
                mcServerSocket = new ServerSocket(MC_LISTENER_PORT);
                System.out.println("[" + new Date() + " WeatherStation] TCP Server started. Listening for Microcontrollers on port " + MC_LISTENER_PORT);

                while (isRunning && !Thread.currentThread().isInterrupted()) {
                    try {
                        Socket clientSocket = mcServerSocket.accept(); 
                        System.out.println("[" + new Date() + " WeatherStation] Accepted connection from Microcontroller: " + clientSocket.getRemoteSocketAddress());
                        
                        mcConnectionHandlerExecutor.submit(new MicrocontrollerConnectionHandler(clientSocket));

                    } catch (SocketException se) {
                        if (!isRunning || mcServerSocket.isClosed()) { 
                            System.out.println("[" + new Date() + " WeatherStation] MC ServerSocket closed or interrupted. Acceptor thread exiting.");
                            break;
                        }

                        System.err.println("[" + new Date() + " WeatherStation] SocketException in MC acceptor loop (e.g. server socket closed): " + se.getMessage());

                    } catch (IOException e) {
                        if (!isRunning) break;
                        System.err.println("[" + new Date() + " WeatherStation] IOException in MC acceptor loop: " + e.getMessage());
                        
                        try { TimeUnit.MILLISECONDS.sleep(100); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                    }
                }
            } catch (IOException e) {
                if (isRunning) {
                    System.err.println("[" + new Date() + " WeatherStation] CRITICAL: Could not start Microcontroller ServerSocket on port " + MC_LISTENER_PORT + ": " + e.getMessage());
                    e.printStackTrace();
                    
                    stop(); 
                }
            } finally {
                if (mcServerSocket != null && !mcServerSocket.isClosed()) {
                    try {
                        mcServerSocket.close();
                    } catch (IOException e) { /* ignore */ }
                }
                System.out.println("[" + new Date() + " WeatherStation] Microcontroller acceptor thread stopped.");
            }
        }, "MC-Acceptor-Thread");
        mcAcceptorThread.start();
    }

    private class MicrocontrollerConnectionHandler implements Runnable {
        private final Socket mcSocket;
        private final String mcAddress;

        public MicrocontrollerConnectionHandler(Socket socket) {
            this.mcSocket = socket;
            this.mcAddress = socket.getRemoteSocketAddress().toString();
            Thread.currentThread().setName("MC-Handler-" + mcHandlerThreadId.incrementAndGet() + "-" + mcAddress);
        }

        @Override
        public void run() {
            System.out.println("[" + new Date() + " WS-MC-Handler for " + mcAddress + "] Thread started.");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(mcSocket.getInputStream()))) {

                String rawDataString = null;
                while (isRunning && !mcSocket.isClosed() && (rawDataString = reader.readLine()) != null) {
                    System.out.println("[" + new Date() + " WS-MC-Handler for " + mcAddress + "] Received raw data: \"" + rawDataString + "\"");
                    processRawData(rawDataString);
                }
                if (rawDataString == null) {
                    System.out.println("[" + new Date() + " WS-MC-Handler for " + mcAddress + "] Microcontroller closed the connection (readLine returned null).");
                }

            } catch (SocketException se) {
                if (mcSocket.isClosed() || !isRunning) {
                     System.out.println("[" + new Date() + " WS-MC-Handler for " + mcAddress + "] Socket closed or station stopping: " + se.getMessage());
                } else {
                    System.err.println("[" + new Date() + " WS-MC-Handler for " + mcAddress + "] SocketException: " + se.getMessage());
                }
            } 
            catch (IOException e) {
                if (isRunning) {
                    System.err.println("[" + new Date() + " WS-MC-Handler for " + mcAddress + "] IOException: " + e.getMessage());
                    // e.printStackTrace();
                }
            } finally {
                try {
                    if (!mcSocket.isClosed()) mcSocket.close();
                } catch (IOException e) {}

                System.out.println("[" + new Date() + " WS-MC-Handler for " + mcAddress + "] Connection closed. Thread finished.");
            }
        }
    }


    private void processRawData(String rawData) {
        try {
            // The data may have a prefix and suffix
            rawData = rawData.trim();
            
            if ((rawData.startsWith("(") || rawData.startsWith("[") || rawData.startsWith("{")) && 
                (rawData.endsWith(")") || rawData.endsWith("]") || rawData.endsWith("}"))) {
                rawData = rawData.substring(1, rawData.length() - 1).trim();
            }

            // Discover the separator used in the raw data
            if (rawData.contains("-")) {
                rawData = rawData.replace("-", "//");
            } else if (rawData.contains(";")) {
                rawData = rawData.replace(";", "//");
            } else if (rawData.contains(",")) {
                rawData = rawData.replace(",", "//");
            } else if (rawData.contains("#")) {
                rawData = rawData.replace("#", "//");
            }

            String[] parts = rawData.split("//");
            if (parts.length == 6) {
                int mcId = Integer.parseInt(parts[0]);
                String mcLocation = parts[1];
                float pressure = Float.parseFloat(parts[2]);
                float radiation = Float.parseFloat(parts[3]);
                float temperature = Float.parseFloat(parts[4]);
                float humidity = Float.parseFloat(parts[5]);

                MicrocontrollerEntity microcontroller = new MicrocontrollerEntity(mcId, mcLocation);
                WeatherData weatherData = new WeatherData(microcontroller, pressure, radiation, temperature, humidity);
                
                System.out.println("[" + new Date() + " WeatherStation] Parsed WeatherData: ID " + weatherData.getId() + " from MC " + mcId);
                sendWeatherData(weatherData);
            } else {
                System.err.println("[" + new Date() + " WeatherStation] Malformed data from MC: \"" + rawData + "\" (Expected 5 parts)");
            }

        } catch (NumberFormatException e) {
            System.err.println("[" + new Date() + " WeatherStation] Error parsing numeric values from MC data \"" + rawData + "\": " + e.getMessage());

        } catch (Exception e) {
            System.err.println("[" + new Date() + " WeatherStation] Unexpected error processing MC data \"" + rawData + "\": " + e.getMessage());
            e.printStackTrace();
        }
    }


    private void sendWeatherData(WeatherData dataToForward) {
        proxyForwarderExecutor.submit(() -> {
            int attempt = 0;
            int maxAttempts = 3;
            long retryDelay = 2000; //ms

            while(attempt < maxAttempts && isRunning) {
                attempt++;
                if (!isConnectedToProxy()) {
                    System.out.println("[" + new Date() + " WeatherStation-ProxyForwarder] Not connected to Proxy. Attempt " + attempt + " to establish connection...");
                    ProxyServerInfo proxyInfo = getProxyAddress();

                    if (proxyInfo == null || !connectToProxy(proxyInfo)) {
                        System.err.println("[" + new Date() + " WeatherStation-ProxyForwarder] Failed to connect to Proxy on attempt " + attempt + ". Cannot forward data for MC " + dataToForward.getMicrocontroller().getId());
                        
                        if (attempt < maxAttempts) {
                            try { TimeUnit.MILLISECONDS.sleep(retryDelay); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                        }

                        continue;
                    }
                }

                if (isConnectedToProxy()) {
                    Message messageToSend = new Message(dataToForward, "INSERT");
                    try {
                        proxyObjectOutStream.writeObject(messageToSend);
                        proxyObjectOutStream.flush();
                        System.out.println("[" + new Date() + " WeatherStation-ProxyForwarder] Successfully forwarded Message for MC " + dataToForward.getMicrocontroller().getId() + " to ProxyServer.");

                        Message reply = (Message) proxyObjectInStream.readObject();
                        System.out.println("[" + new Date() + " WeatherStation-ProxyForwarder] Received reply from Proxy. Instruction: \"" + reply.getInstrucao() + "\" for MC " + dataToForward.getMicrocontroller().getId());
                        return;

                    } catch (SocketException se) {
                        System.err.println("[" + new Date() + " WeatherStation-ProxyForwarder] SocketException (Proxy): " + se.getMessage() + " for MC " + dataToForward.getMicrocontroller().getId() + ". Attempt " + attempt + ". Connection lost.");
                        disconnectFromProxy();

                    } catch (IOException e) {
                        System.err.println("[" + new Date() + " WeatherStation-ProxyForwarder] IOException (Proxy): " + e.getMessage() + " for MC " + dataToForward.getMicrocontroller().getId() + ". Attempt " + attempt);
                        disconnectFromProxy();

                    } catch (ClassNotFoundException e) {
                        System.err.println("[" + new Date() + " WeatherStation-ProxyForwarder] CRITICAL: Message class not found (Proxy reply). Halting. " + e.getMessage());
                        this.stop();
                        return;

                    } catch (Exception e) {
                        System.err.println("[" + new Date() + " WeatherStation-ProxyForwarder] Unexpected error (Proxy): " + e.getMessage() + " for MC " + dataToForward.getMicrocontroller().getId());
                        e.printStackTrace();
                        disconnectFromProxy();
                    }
                }
                if (attempt < maxAttempts) {
                     try { TimeUnit.MILLISECONDS.sleep(retryDelay); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                }
            }
            if (isRunning) {
               System.err.println("[" + new Date() + " WeatherStation-ProxyForwarder] Failed to forward data for MC " + dataToForward.getMicrocontroller().getId() + " after " + maxAttempts + " attempts.");
            }
        });
    }

    private ProxyServerInfo getProxyAddress() {
        try (Socket socket = new Socket(locationServerHost, locationServerPort);
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
             DataInputStream dis = new DataInputStream(socket.getInputStream())) {
            socket.setSoTimeout(5000);
            dos.writeUTF(LOCATION_SERVER_AUTH_KEY);
            dos.flush();

            String proxyHost = dis.readUTF();
            int proxyPort = dis.readInt();

            ProxyServerInfo proxyInfo = new ProxyServerInfo(proxyHost, proxyPort);
            if (proxyInfo.isValid()) {
                System.out.println("[" + new Date() + " WeatherStation] Received ProxyServer details: " + proxyInfo);
                return proxyInfo;
            } else {
                System.err.println("[" + new Date() + " WeatherStation] LocationServer returned invalid Proxy details (Host: '" + proxyHost + "', Port: " + proxyPort + ").");
                return null;
            }

        } catch (UnknownHostException e) {
            System.err.println("[" + new Date() + " WeatherStation] Error: LocationServer host '" + locationServerHost + "' not found. " + e.getMessage());

        } catch (IOException e) {
            System.err.println("[" + new Date() + " WeatherStation] Error: IOException with LocationServer " + locationServerHost + ":" + locationServerPort + ". " + e.getMessage());
        }
        return null;
    }

    private boolean connectToProxy(ProxyServerInfo proxyInfo) {
        if (proxyInfo == null || !proxyInfo.isValid()) {
            System.err.println("[" + new Date() + " WeatherStation] Invalid ProxyServer info for connection.");
            return false;
        }

        try {
            disconnectFromProxy(); 
            System.out.println("[" + new Date() + " WeatherStation] Connecting to ProxyServer: " + proxyInfo + "...");

            proxySocket = new Socket(proxyInfo.host, proxyInfo.port);
            proxyObjectOutStream = new ObjectOutputStream(proxySocket.getOutputStream());
            proxyObjectInStream = new ObjectInputStream(proxySocket.getInputStream());

            System.out.println("[" + new Date() + " WeatherStation] Successfully connected to ProxyServer: " + proxyInfo);
            return true;

        } catch (UnknownHostException e) {
            System.err.println("[" + new Date() + " WeatherStation] Error: ProxyServer host '" + proxyInfo.host + "' not found. " + e.getMessage());

        } catch (IOException e) {
            System.err.println("[" + new Date() + " WeatherStation] Error: IOException connecting to ProxyServer " + proxyInfo + ". " + e.getMessage());
        }
        return false;
    }

    private void disconnectFromProxy() {
        try { if (proxyObjectOutStream != null) proxyObjectOutStream.close(); } catch (IOException e) { /* ignore */ }
        try { if (proxyObjectInStream != null) proxyObjectInStream.close(); } catch (IOException e) { /* ignore */ }
        try { if (proxySocket != null && !proxySocket.isClosed()) proxySocket.close(); } catch (IOException e) { /* ignore */ }

        proxyObjectOutStream = null; proxyObjectInStream = null; proxySocket = null;
    }

    public boolean isConnectedToProxy() {
        return proxySocket != null && proxySocket.isConnected() && !proxySocket.isClosed() &&
               proxyObjectOutStream != null && proxyObjectInStream != null;
    }
}