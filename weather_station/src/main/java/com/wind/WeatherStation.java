package com.wind; 

import com.wind.service.RabbitMQService;
import com.wind.service.ServiceDiscoveryService;
import com.wind.service.UdpService;

import java.util.UUID;

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

    private ServiceDiscoveryService serviceDiscoveryService;
    private RabbitMQService rabbitMQService;
    private UdpService udpService;

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
            // Initialize Services
            this.serviceDiscoveryService = new ServiceDiscoveryService(SERVICE_DISCOVERY_URL, SERVICE_NAME, INSTANCE_ID, SERVICE_ADDRESS);
            this.rabbitMQService = new RabbitMQService(RABBITMQ_HOST, RABBITMQ_USER, RABBITMQ_PASS, RABBITMQ_EXCHANGE_NAME);
            this.udpService = new UdpService(INGRESS_PORT, EGRESS_HOST, EGRESS_PORT);

            System.out.println("[INIT] Connecting to RabbitMQ (" + RABBITMQ_HOST + ")...");
            rabbitMQService.connect();

            System.out.println("[INIT] Setting up Egress UDP Socket (" + EGRESS_HOST + ":" + EGRESS_PORT + ")...");
            udpService.setupEgress();

            System.out.println("[INIT] Starting Ingress UDP Listener on port " + INGRESS_PORT + "...");
            udpService.startIngressListener(this::processMessage);

            System.out.println("[INIT] Registering with Service Discovery...");
            serviceDiscoveryService.registerService();
            serviceDiscoveryService.startHeartbeat();

            System.out.println("\nWeatherStation is running and listening for UDP packets. Press Ctrl+C to stop.");

        } catch (Exception e) {
            System.err.println("WeatherStation failed to start: " + e.getMessage());
            e.printStackTrace();
            stop();
        }
    }

    public void stop() {
        if (serviceDiscoveryService != null) {
            serviceDiscoveryService.deregisterService();
            serviceDiscoveryService.stop();
        }
        if (udpService != null) {
            udpService.stop();
        }
        if (rabbitMQService != null) {
            rabbitMQService.close();
        }
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
        if (udpService != null) {
            udpService.send(processedPayload);
        }
        
        if (rabbitMQService != null) {
            rabbitMQService.publish(processedPayload);
        }
    }
}