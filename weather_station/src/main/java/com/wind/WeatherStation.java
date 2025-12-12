package com.wind; 

import com.wind.entities.MicrocontrollerEntity;
import com.wind.model.DAO.MicrocontrollerDAO;
import com.wind.service.ManagementService;
import com.wind.service.RabbitMQService;
import com.wind.service.ServiceDiscoveryService;
import com.wind.service.UdpService;
import com.wind.security.RSA;
import com.wind.security.AES;

import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

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
    private static final String SERVICE_NAME_UDP = "weather-station-udp";
    private static final String INSTANCE_ID = "weather-station-" + UUID.randomUUID();
    private static final String INSTANCE_ID_UDP = "weather-station-udp-" + UUID.randomUUID();
    // Use internal docker address for management API discovery
    private static final String SERVICE_ADDRESS = System.getenv().getOrDefault("MANAGEMENT_ADDRESS", "weather-station:9090");

    // Management
    private static final int MANAGEMENT_PORT = Integer.parseInt(System.getenv().getOrDefault("MANAGEMENT_PORT", "9090"));

    private ServiceDiscoveryService serviceDiscoveryService;
    private ServiceDiscoveryService udpDiscoveryService;
    private RabbitMQService rabbitMQService;
    private UdpService udpService;
    private ManagementService managementService;
    private MicrocontrollerDAO microcontrollerDAO;
    
    private PrivateKey privateKey;
    private PublicKey publicKey;
    private final Map<Integer, byte[]> microcontrollerKeys = new ConcurrentHashMap<>();

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
            // Initialize Security Keys
            KeyPair keyPair = RSA.generateKeyPair();
            this.privateKey = keyPair.getPrivate();
            this.publicKey = keyPair.getPublic();

            // Initialize DAO
            this.microcontrollerDAO = new MicrocontrollerDAO();

            // Initialize Services
            this.serviceDiscoveryService = new ServiceDiscoveryService(SERVICE_DISCOVERY_URL, SERVICE_NAME, INSTANCE_ID, SERVICE_ADDRESS);
            this.udpDiscoveryService = new ServiceDiscoveryService(SERVICE_DISCOVERY_URL, SERVICE_NAME_UDP, INSTANCE_ID_UDP, EGRESS_HOST + ":" + EGRESS_PORT);
            this.rabbitMQService = new RabbitMQService(RABBITMQ_HOST, RABBITMQ_USER, RABBITMQ_PASS, RABBITMQ_EXCHANGE_NAME);
            this.udpService = new UdpService(INGRESS_PORT, EGRESS_HOST, EGRESS_PORT);
            this.managementService = new ManagementService(MANAGEMENT_PORT, microcontrollerDAO, publicKey, privateKey, microcontrollerKeys);

            System.out.println("[INIT] Connecting to RabbitMQ (" + RABBITMQ_HOST + ")...");
            rabbitMQService.connect();

            System.out.println("[INIT] Setting up Egress UDP Socket (" + EGRESS_HOST + ":" + EGRESS_PORT + ")...");
            udpService.setupEgress();

            System.out.println("[INIT] Starting Ingress UDP Listener on port " + INGRESS_PORT + "...");
            udpService.startIngressListener(this::processMessage);

            System.out.println("[INIT] Starting Management Service on port " + MANAGEMENT_PORT + "...");
            managementService.start();

            System.out.println("[INIT] Registering with Service Discovery...");
            serviceDiscoveryService.registerService();
            serviceDiscoveryService.startHeartbeat();
            
            System.out.println("[INIT] Registering UDP Service with Service Discovery...");
            udpDiscoveryService.registerService();
            udpDiscoveryService.startHeartbeat();

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
        if (udpDiscoveryService != null) {
            udpDiscoveryService.deregisterService();
            udpDiscoveryService.stop();
        }
        if (udpService != null) {
            udpService.stop();
        }
        if (rabbitMQService != null) {
            rabbitMQService.close();
        }
        if (managementService != null) {
            managementService.stop();
        }
    }

    private void processMessage(String rawPayload, InetSocketAddress sender) {
        String processedPayload = rawPayload.trim();
        
        // Try to parse ID and Encrypted Payload
        // Format: ID|EncryptedBase64
        String[] parts = processedPayload.split("\\|");
        
        if (parts.length == 2) {
            try {
                int id = Integer.parseInt(parts[0]);
                String encryptedData = parts[1];
                
                if (microcontrollerKeys.containsKey(id)) {
                    byte[] keyBytes = microcontrollerKeys.get(id);
                    SecretKey key = new SecretKeySpec(keyBytes, "AES");
                    AES aes = new AES(key);
                    
                    String decrypted = aes.decrypt(encryptedData);
                    if (decrypted != null) {
                        processedPayload = decrypted.trim();
                        System.out.println("   [DECRYPTED] Payload from ID " + id + ": " + processedPayload);
                    } else {
                        System.err.println("   [ERROR] Failed to decrypt payload from ID " + id);
                        return;
                    }
                } else {
                    System.out.println("   [UNKNOWN KEY] No key found for ID " + id + ". Is handshake done?");
                }
            } catch (NumberFormatException e) {
                //
            }
        }

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

        // Validate if the Microcontroller is registered
        try {
            parts = processedPayload.split("\\|");
            if (parts.length > 0) {
                // Extract ID (remove non-numeric prefix if present, e.g., "A1" -> "1")
                String idString = parts[0];
                
                // Handle potential prefix in ID (e.g. "A1")
                if (idString.matches(".*\\d.*")) {
                     idString = idString.replaceAll("\\D+","");
                }

                if (!idString.isEmpty()) {
                    int id = Integer.parseInt(idString);
                    MicrocontrollerEntity mc = microcontrollerDAO.getMicrocontroller(id);

                    if (mc == null) {
                        System.out.println("   [ACCESS DENIED] Ignored data from unregistered Microcontroller ID: " + id);
                        return; // Stop processing this message
                    }
                } else {
                    System.out.println("   [INVALID] Could not parse ID from payload: " + parts[0]);
                    return;
                }
            }
        } catch (Exception e) {
            System.err.println("   [ERROR] Malformed payload during validation: " + e.getMessage());
            return;
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