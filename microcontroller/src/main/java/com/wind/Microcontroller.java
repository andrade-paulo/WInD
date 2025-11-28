package com.wind;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.IOException;

import java.util.Date;
import java.util.Locale;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.security.PublicKey;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import com.wind.security.RSA;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import com.wind.security.AES;

public class Microcontroller {

    private final int id;
    private final String location;
    private final String serverHost;
    private final int serverPort;
    private final int localPort; // New field for the local binding port

    private final Character prefix;
    private final Character suffix;
    private final Character separator;

    private volatile boolean isRunning = true;
    private final Random random;
    private DatagramSocket socket;
    private SecretKey aesKey;
    private final HttpClient httpClient;
    private static final String API_KEY = "super-secret-key-123";
    private static final String KEY_FILE_PREFIX = "mc_key_";

    public Microcontroller(int id, String location, String serverHost, int serverPort, int localPort, Character prefix, Character suffix, Character separator) {
        this.id = id;
        // Valida a localização para garantir que seja uma das opções esperadas
        this.location = location;
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.localPort = localPort;
        
        this.prefix = prefix != null ? prefix : '\0';
        this.suffix = suffix != null ? suffix : '\0';
        this.separator = separator != null ? separator : ';';

        this.random = new Random();
        this.httpClient = HttpClient.newHttpClient();
    }

    public static void main(String[] args) {
        System.out.println("WInD - Microcontroller Simulation (UDP Version)");

        try (Scanner scanner = new Scanner(System.in)) {
            // Print it's own IP address
            try {
                InetAddress localAddress = InetAddress.getLocalHost();
                System.out.println("Local IP Address: " + localAddress.getHostAddress());
            } catch (IOException e) {
                System.err.println("Error retrieving local IP address: " + e.getMessage());
            }

            System.out.print("ID: ");
            int id = scanner.nextInt();
            scanner.nextLine(); // Consume newline

            // Força a escolha de uma das quatro localizações
            String location;
            while (true) {
                System.out.print("Location (North, South, East, West): ");
                location = scanner.nextLine().trim();
                if (location.equalsIgnoreCase("North") || location.equalsIgnoreCase("South") ||
                    location.equalsIgnoreCase("East")  || location.equalsIgnoreCase("West")) {
                    break;
                }
                System.out.println("Invalid location. Please choose one of the four options.");
            }

            // Prefixo, Sufixo e Separador
            System.out.print("Prefix (optional): ");
            String prefixInput = scanner.nextLine();
            Character prefix = prefixInput.isEmpty() ? null : prefixInput.charAt(0);
            
            System.out.print("Suffix (optional): ");
            String suffixInput = scanner.nextLine();
            Character suffix = suffixInput.isEmpty() ? null : suffixInput.charAt(0);
            
            System.out.print("Separator (default ';'): ");
            String separatorInput = scanner.nextLine();
            Character separator = separatorInput.isEmpty() ? null : separatorInput.charAt(0);

            // Server Configuration
            System.out.print("Server Host (default: localhost): ");
            String serverHost = scanner.nextLine().trim();
            if (serverHost.isEmpty()) {
                serverHost = "localhost";
            }

            System.out.print("Server Port (default: 9876): ");
            String portStr = scanner.nextLine().trim();
            int serverPort = portStr.isEmpty() ? 9876 : Integer.parseInt(portStr);

            // Local Port Configuration removed - using random port
            int localPort = 0;

            System.out.println("===================================================");
            System.out.println("        Microcontroller Simulation Starting");
            System.out.println("  Targeting UDP Server at " + serverHost + ":" + serverPort);
            System.out.println("  Binding to Random Local Port");
            System.out.println("===================================================");

            Microcontroller mc = new Microcontroller(id, location, serverHost, serverPort, localPort, prefix, suffix, separator);
            Thread mcThread = new Thread(mc::start, "UDP-MC-" + id);
            mcThread.start();

            // Shutdown hook para garantir desconexão limpa
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n[" + new Date() + " Main] Shutdown hook triggered for Microcontroller " + id);
                mc.stopSendingData();
                try {
                    mcThread.join(2000); // Espera o thread do microcontrolador terminar
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                System.out.println("[" + new Date() + " Main] Microcontroller " + id + " shutdown complete.");
            }));

            System.out.println("[" + new Date() + " Main] Microcontroller " + id + " launched. Press Ctrl+C to stop.");
        }
    }

    private boolean connect() {
        try {
            if (socket == null || socket.isClosed()) {
                // Bind to the specific local port if provided
                if (localPort > 0) {
                    socket = new DatagramSocket(localPort);
                } else {
                    socket = new DatagramSocket();
                }
                System.out.println("[UDP] Socket created. Bound to local port: " + socket.getLocalPort());
            }
            return true;
        } catch (IOException e) {
            System.err.println("Error creating socket: " + e.getMessage());
            if (localPort > 0 && localPort < 1024) {
                System.err.println("Hint: Port " + localPort + " is a privileged port. Try using a port > 1024.");
            }
            return false;
        }
    }

    private void disconnect() {
        if (socket != null && !socket.isClosed()) {
            System.out.println("[" + new Date() + " MC-" + id + "] Closing UDP socket...");
            socket.close();
            System.out.println("[" + new Date() + " MC-" + id + "] Socket closed.");
        }
    }

    public void start() {
        try {
            // Perform Handshake
            performHandshake();

            socket = new DatagramSocket(localPort);
            System.out.println("Microcontroller " + id + " started on port " + socket.getLocalPort());
            System.out.println("Sending data to " + serverHost + ":" + serverPort);

            while (isRunning) {
                sendData();
                // Wait for 2 seconds before sending next data
                TimeUnit.SECONDS.sleep(2);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
    }

    private void saveKey(SecretKey key) {
        try (FileOutputStream fos = new FileOutputStream(KEY_FILE_PREFIX + id + ".dat")) {
            fos.write(key.getEncoded());
        } catch (IOException e) {
            System.err.println("Error saving key: " + e.getMessage());
        }
    }

    private boolean loadKey() {
        File keyFile = new File(KEY_FILE_PREFIX + id + ".dat");
        if (keyFile.exists()) {
            try (FileInputStream fis = new FileInputStream(keyFile)) {
                byte[] keyBytes = fis.readAllBytes();
                this.aesKey = new SecretKeySpec(keyBytes, "AES");
                return true;
            } catch (IOException e) {
                System.err.println("Error loading key: " + e.getMessage());
            }
        }
        return false;
    }

    private void performHandshake() throws Exception {
        // Assuming the gateway is at http://localhost:8000 for simplicity in this simulation
        // In a real scenario, this URL should be configurable
        // Updated to point to Weather Station Management Port (9090)
        String gatewayUrl = "http://localhost:9090";

        // 1. Get Server Public Key
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(gatewayUrl + "/security/public-key"))
                .header("X-API-Key", API_KEY)
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new Exception("Failed to retrieve public key");
        }
        
        PublicKey serverPublicKey = RSA.getPublicKeyFromBase64(response.body());

        // 2. Load or Generate AES Key
        boolean keyLoaded = loadKey();
        if (!keyLoaded) {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(256);
            aesKey = keyGen.generateKey();
        } else {
            System.out.println("Loaded existing AES key for ID " + id);
        }

        // 3. Encrypt AES Key with RSA
        String encryptedAesKey = RSA.encrypt(aesKey.getEncoded(), serverPublicKey);

        // 4. Send Encrypted AES Key to Server
        // Include ID in the query parameter
        request = HttpRequest.newBuilder()
                .uri(URI.create(gatewayUrl + "/security/handshake?mcId=" + id))
                .header("X-API-Key", API_KEY)
                .POST(HttpRequest.BodyPublishers.ofString(encryptedAesKey))
                .build();
        
        response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 409) {
            if (keyLoaded) {
                System.out.println("Server already has key for ID " + id + ". Proceeding with loaded key.");
            } else {
                System.err.println("Erro: ID " + id + " já registrado no servidor. Handshake recusado.");
                // In a real scenario, we might want to load a persisted key here
                throw new Exception("Microcontroller ID already registered");
            }
        } else if (response.statusCode() != 200) {
            throw new Exception("Handshake failed with status: " + response.statusCode());
        } else {
            // If 200 OK and we generated a new key, save it
            if (!keyLoaded) {
                saveKey(aesKey);
                System.out.println("New key generated and saved.");
            }
            System.out.println("Handshake de segurança realizado com sucesso.");
        }
    }

    private void sendData() throws IOException {
        // Gera dados climáticos aleatórios
        float pressure = 950.0f + random.nextFloat() * (1050.0f - 950.0f);
        float radiation = random.nextFloat() * 1400.0f;
        float temperature = 253.15f + random.nextFloat() * (323.15f - 253.15f);
        float humidity = random.nextFloat() * 100.0f;

        String dataString = String.format(Locale.US, "%c%d%c%s%c%.2f%c%.2f%c%.2f%c%.2f%c",
            this.prefix, this.id, this.separator, location, this.separator, pressure, this.separator, radiation, this.separator, temperature, this.separator, humidity, this.suffix);

        // Encrypt Payload
        AES aes = new AES(aesKey);
        byte[] encryptedBytes = aes.encrypt(dataString.getBytes());
        String encryptedBase64 = Base64.getEncoder().encodeToString(encryptedBytes);
        
        // Format: ID|EncryptedData
        String payload = id + "|" + encryptedBase64;

        byte[] buffer = payload.getBytes();
        InetAddress address = InetAddress.getByName(serverHost);
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, serverPort);

        socket.send(packet);
        System.out.println("[" + new Date() + " MC-" + id + "] Sent UDP packet to " + serverHost + ":" + serverPort + " (from port " + socket.getLocalPort() + ") : " + payload);
    }

    public void stopSendingData() {
        this.isRunning = false;
    }
}