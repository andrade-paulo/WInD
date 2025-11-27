package com.wind; 

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class WeatherStation {

    // Configuração dos Brokers / UDP
    private static final int INGRESS_PORT = Integer.parseInt(System.getenv().getOrDefault("INGRESS_PORT", "9876"));
    
    private static final String EGRESS_HOST = System.getenv().getOrDefault("EGRESS_HOST", "localhost");
    private static final int EGRESS_PORT = Integer.parseInt(System.getenv().getOrDefault("EGRESS_PORT", "9877"));

    private static final String RABBITMQ_HOST = System.getenv().getOrDefault("RABBITMQ_HOST", "localhost");
    private static final String RABBITMQ_USER = System.getenv().getOrDefault("RABBITMQ_USER", "winduser");
    private static final String RABBITMQ_PASS = System.getenv().getOrDefault("RABBITMQ_PASS", "windpass");
    private static final String RABBITMQ_EXCHANGE_NAME = "wind_events_exchange";

    // Service Discovery
    private static final String SERVICE_DISCOVERY_URL = System.getenv().getOrDefault("SERVICE_DISCOVERY_URL", "http://localhost:7000");
    private static final String SERVICE_NAME = "weather-station";
    private static final String INSTANCE_ID = "weather-station-" + UUID.randomUUID();
    private static final String SERVICE_ADDRESS = EGRESS_HOST + ":" + EGRESS_PORT;

    // Conexões
    private DatagramSocket ingressSocket;
    private DatagramSocket egressSocket;
    private Connection rabbitConnection;
    private Channel rabbitChannel;
    private volatile boolean isRunning = true;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private ScheduledExecutorService heartbeatScheduler;

    public static void main(String[] args) {
        System.out.println("WInD - WeatherStation Service (UDP Version)");
        System.out.println("======================");

        WeatherStation gateway = new WeatherStation();
        gateway.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nShutdown hook triggered. Disconnecting from all services...");
            gateway.stop();
            System.out.println("WeatherStation shutdown complete.");
        }));
    }

    public void start() {
        try {
            System.out.println("[INIT] Connecting to RabbitMQ (" + RABBITMQ_HOST + ")...");
            connectRabbitMQ();

            System.out.println("[INIT] Setting up Egress UDP Socket (" + EGRESS_HOST + ":" + EGRESS_PORT + ")...");
            setupEgressUdp();

            System.out.println("[INIT] Starting Ingress UDP Listener on port " + INGRESS_PORT + "...");
            startIngressListener();

            System.out.println("[INIT] Registering with Service Discovery...");
            registerService();
            startHeartbeat();

            System.out.println("\nWeatherStation is running and listening for UDP packets. Press Ctrl+C to stop.");

        } catch (Exception e) {
            System.err.println("WeatherStation failed to start: " + e.getMessage());
            e.printStackTrace();
            stop();
        }
    }

    public void stop() {
        isRunning = false;
        deregisterService();
        if (heartbeatScheduler != null) {
            heartbeatScheduler.shutdown();
        }
        if (ingressSocket != null && !ingressSocket.isClosed()) {
            ingressSocket.close();
        }
        if (egressSocket != null && !egressSocket.isClosed()) {
            egressSocket.close();
        }
        try {
            if (rabbitChannel != null && rabbitChannel.isOpen()) rabbitChannel.close();
            if (rabbitConnection != null && rabbitConnection.isOpen()) rabbitConnection.close();
        } catch (IOException | TimeoutException e) {
            System.err.println("Error disconnecting from RabbitMQ: " + e.getMessage());
        }
    }

    private void connectRabbitMQ() throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(RABBITMQ_HOST);
        factory.setUsername(RABBITMQ_USER);
        factory.setPassword(RABBITMQ_PASS);
        this.rabbitConnection = factory.newConnection();
        this.rabbitChannel = rabbitConnection.createChannel();
        this.rabbitChannel.exchangeDeclare(RABBITMQ_EXCHANGE_NAME, "fanout", true);
        System.out.println("[RabbitMQ] Connected and exchange '" + RABBITMQ_EXCHANGE_NAME + "' is ready.");
    }

    private void setupEgressUdp() throws SocketException {
        this.egressSocket = new DatagramSocket();
        System.out.println("[Egress UDP] Socket created.");
    }

    private void startIngressListener() {
        new Thread(() -> {
            try {
                ingressSocket = new DatagramSocket(INGRESS_PORT);
                byte[] buffer = new byte[1024];
                
                while (isRunning) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    try {
                        ingressSocket.receive(packet);
                        String rawPayload = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                        
                        System.out.println("\n-> [UDP RCV] From: " + packet.getAddress() + ":" + packet.getPort());
                        System.out.println("   Payload: " + rawPayload);
                        
                        processMessage(rawPayload);
                        
                    } catch (IOException e) {
                        if (isRunning) {
                            System.err.println("[Ingress UDP] Error receiving packet: " + e.getMessage());
                        }
                    }
                }
            } catch (SocketException e) {
                System.err.println("[Ingress UDP] Could not bind to port " + INGRESS_PORT + ": " + e.getMessage());
            }
        }).start();
    }

    private void processMessage(String rawPayload) {
        String processedPayload = rawPayload.trim();
        
        if ((processedPayload.startsWith("(") || processedPayload.startsWith("[") || processedPayload.startsWith("{")) && 
            (processedPayload.endsWith(")") || processedPayload.endsWith("]") || processedPayload.endsWith("}"))) {
                processedPayload = processedPayload.substring(1, processedPayload.length() - 1).trim();
        }

        // Discover the separator used in the raw data
        if (processedPayload.contains("-")) {
            processedPayload = processedPayload.replace("-", "|");
        } else if (processedPayload.contains(";")) {
            processedPayload = processedPayload.replace(";", "|");
        } else if (processedPayload.contains(",")) {
            processedPayload = processedPayload.replace(",", "|");
        } else if (processedPayload.contains("#")) {
            processedPayload = processedPayload.replace("#", "|");
        }

        System.out.println("   Processed Payload: " + processedPayload);

        // In UDP we don't have topics, so we just forward the payload
        publishToEgress(processedPayload);
        
        publishToRabbitMQ(processedPayload);
    }

    private void publishToEgress(String payload) {
        try {
            if (egressSocket != null && !egressSocket.isClosed()) {
                byte[] data = payload.getBytes(StandardCharsets.UTF_8);
                InetAddress address = InetAddress.getByName(EGRESS_HOST);
                DatagramPacket packet = new DatagramPacket(data, data.length, address, EGRESS_PORT);
                
                egressSocket.send(packet);
                System.out.println("<- [EGRESS UDP] Sent to " + EGRESS_HOST + ":" + EGRESS_PORT);
            } else {
                System.err.println("[EGRESS UDP] Cannot send, socket not available.");
            }
        } catch (IOException e) {
            System.err.println("[EGRESS UDP] Error sending packet: " + e.getMessage());
        }
    }
    
    private void publishToRabbitMQ(String payload) {
        try {
             if (rabbitChannel != null && rabbitChannel.isOpen()) {
                rabbitChannel.basicPublish(RABBITMQ_EXCHANGE_NAME, "", null, payload.getBytes(StandardCharsets.UTF_8));
                System.out.println("<- [RABBITMQ] Published to exchange: " + RABBITMQ_EXCHANGE_NAME);
             } else {
                 System.err.println("[RABBITMQ] Cannot publish, channel not available.");
             }
        } catch (IOException e) {
             System.err.println("[RABBITMQ] Error publishing to RabbitMQ: " + e.getMessage());
        }
    }

    private void registerService() {
        try {
            Map<String, Object> serviceInfo = new HashMap<>();
            serviceInfo.put("serviceName", SERVICE_NAME);
            serviceInfo.put("instanceId", INSTANCE_ID);
            serviceInfo.put("address", SERVICE_ADDRESS);
            serviceInfo.put("healthCheckUrl", null); // No HTTP health check for now

            String jsonBody = objectMapper.writeValueAsString(serviceInfo);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(SERVICE_DISCOVERY_URL + "/register"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 201) {
                System.out.println("[Service Discovery] Registered successfully as " + INSTANCE_ID);
            } else {
                System.err.println("[Service Discovery] Registration failed: " + response.statusCode() + " - " + response.body());
            }
        } catch (Exception e) {
            System.err.println("[Service Discovery] Error registering service: " + e.getMessage());
        }
    }

    private void startHeartbeat() {
        heartbeatScheduler = Executors.newSingleThreadScheduledExecutor();
        heartbeatScheduler.scheduleAtFixedRate(this::sendHeartbeat, 10, 30, TimeUnit.SECONDS);
    }

    private void sendHeartbeat() {
        try {
            Map<String, Object> heartbeatInfo = new HashMap<>();
            heartbeatInfo.put("serviceName", SERVICE_NAME);
            heartbeatInfo.put("instanceId", INSTANCE_ID);

            String jsonBody = objectMapper.writeValueAsString(heartbeatInfo);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(SERVICE_DISCOVERY_URL + "/heartbeat"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 404) {
                System.err.println("[Service Discovery] Instance not found during heartbeat. Re-registering...");
                registerService();
            } else if (response.statusCode() != 200) {
                System.err.println("[Service Discovery] Heartbeat failed: " + response.statusCode());
            }
        } catch (Exception e) {
            System.err.println("[Service Discovery] Error sending heartbeat: " + e.getMessage());
        }
    }

    private void deregisterService() {
        try {
            Map<String, Object> serviceInfo = new HashMap<>();
            serviceInfo.put("serviceName", SERVICE_NAME);
            serviceInfo.put("instanceId", INSTANCE_ID);

            String jsonBody = objectMapper.writeValueAsString(serviceInfo);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(SERVICE_DISCOVERY_URL + "/deregister"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("[Service Discovery] Deregistered successfully.");
        } catch (Exception e) {
            System.err.println("[Service Discovery] Error deregistering service: " + e.getMessage());
        }
    }
}