package com.wind;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wind.entities.MicrocontrollerEntity;
import com.wind.model.LogCSV;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Scanner;

public class EngClient {
    private static final String API_KEY = "super-secret-key-123";
    private static final String WEATHER_STATION_SERVICE_NAME = "weather-station";
    private static final int MANAGEMENT_PORT = 9090; // Default management port

    private DatagramSocket udpSocket;
    private volatile boolean isRunning = true;
    private volatile boolean isMonitoring = false;
    private final Scanner scanner;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private String gatewayUrl;
    private String weatherStationManagementUrl;

    private LogCSV logCSV = new LogCSV();

    public EngClient() {
        this.scanner = new Scanner(System.in);
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    public static void main(String[] args) {
        System.out.println("WInD - Realtime Client Viewer (UDP Version)");
        System.out.println("===========================");

        EngClient client = new EngClient();
        client.start();
    }

    public void start() {
        try {
            System.out.print("Insira a URL do API Gateway (ex: http://localhost:8000): ");
            String inputUrl = scanner.nextLine().trim();
            this.gatewayUrl = inputUrl.isEmpty() ? "http://localhost:8000" : inputUrl;

            if (!this.gatewayUrl.toLowerCase().startsWith("http://") && !this.gatewayUrl.toLowerCase().startsWith("https://")) {
                this.gatewayUrl = "http://" + this.gatewayUrl;
            }

            System.out.println("[INIT] Requesting WeatherStation address from API Gateway...");
            String address = getServiceAddress(WEATHER_STATION_SERVICE_NAME);
            
            if (address == null) {
                System.err.println("Could not find WeatherStation service. Exiting.");
                return;
            }

            System.out.println("[DISCOVERY] WeatherStation found at: " + address);
            
            // Clean up address (remove quotes and brackets if present)
            address = address.replace("\"", "").replace("[", "").replace("]", "").trim();

            // Parse port from address (assuming host:port)
            String[] parts = address.split(":");
            String host = parts[0];
            
            // Fix for local docker environment where host.docker.internal is not resolvable by client
            if ("host.docker.internal".equals(host)) {
                host = "localhost";
            }

            int port = Integer.parseInt(parts[1]);

            // Construct Management URL
            this.weatherStationManagementUrl = "http://" + host + ":" + MANAGEMENT_PORT;
            System.out.println("[INIT] WeatherStation Management URL: " + this.weatherStationManagementUrl);

            System.out.println("[INIT] Starting UDP Listener on port " + port + "...");
            startUdpListener(port);

            // Loop principal do menu
            mainMenuLoop();

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            stop();
        }
    }

    private String getServiceAddress(String serviceName) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(gatewayUrl + "/services/" + serviceName))
                    .header("X-API-Key", API_KEY)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return response.body();
            } else {
                System.err.println("API Gateway returned error: " + response.statusCode() + " - " + response.body());
                return null;
            }
        } catch (Exception e) {
            System.err.println("Failed to contact API Gateway: " + e.getMessage());
            return null;
        }
    }

    private void startUdpListener(int port) {
        new Thread(() -> {
            try {
                udpSocket = new DatagramSocket(port);
                byte[] buffer = new byte[1024];

                while (isRunning) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    try {
                        udpSocket.receive(packet);
                        String payload = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                        processMessage(payload);
                    } catch (IOException e) {
                        if (isRunning) {
                            System.err.println("Error receiving UDP packet: " + e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Could not bind to UDP port " + port + ": " + e.getMessage());
            }
        }).start();
    }

    private void processMessage(String payload) {
        if (!isMonitoring) return;

        String[] parts = payload.split("\\|");
        if (parts.length < 2) return;

        String location = parts[1];

        System.out.printf("[DADO RECEBIDO] Local: %s | Dados: %s%n", location, payload);
        logCSV.log("udp/realtime/" + location, payload);
    }

    private void mainMenuLoop() {
        boolean exit = false;
        while (!exit) {
            displayMenu();
            System.out.print("Escolha uma opção: ");
            String choice = scanner.nextLine();

            switch (choice) {
                case "1":
                    System.out.println("Monitorando dados... (Pressione Enter para parar)");
                    isMonitoring = true;
                    scanner.nextLine();
                    isMonitoring = false;
                    System.out.println("Monitoramento pausado.");
                    break;
                case "2":
                    listMicrocontrollers();
                    break;
                case "3":
                    registerMicrocontroller();
                    break;
                case "4":
                    removeMicrocontroller();
                    break;
                case "0":
                    exit = true;
                    break;
                default:
                    System.out.println("Opção inválida. Tente novamente.");
                    break;
            }
        }
    }

    private void displayMenu() {
        System.out.println("\n--- MENU DE VISUALIZAÇÃO ---");
        System.out.println("1. Monitorar Dados");
        System.out.println("2. Listar Microcontroladores");
        System.out.println("3. Cadastrar Microcontrolador");
        System.out.println("4. Remover Microcontrolador");
        System.out.println("0. Sair");
        System.out.println("----------------------------");
    }

    private void listMicrocontrollers() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(weatherStationManagementUrl + "/microcontrollers"))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                List<MicrocontrollerEntity> list = objectMapper.readValue(response.body(), new TypeReference<List<MicrocontrollerEntity>>() {});
                System.out.println("\n--- Microcontroladores Cadastrados ---");
                if (list.isEmpty()) {
                    System.out.println("Nenhum microcontrolador encontrado.");
                } else {
                    for (MicrocontrollerEntity mc : list) {
                        System.out.println(mc);
                        System.out.println("-------------------------");
                    }
                }
            } else {
                System.err.println("Erro ao listar microcontroladores: " + response.statusCode());
            }
        } catch (Exception e) {
            System.err.println("Erro: " + e.getMessage());
        }
    }

    private void registerMicrocontroller() {
        try {
            System.out.println("\n--- Cadastro de Microcontrolador ---");
            System.out.print("ID (0 para gerar automático): ");
            int id = Integer.parseInt(scanner.nextLine());
            System.out.print("Região: ");
            String region = scanner.nextLine();

            MicrocontrollerEntity mc = new MicrocontrollerEntity(id, region);
            String json = objectMapper.writeValueAsString(mc);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(weatherStationManagementUrl + "/microcontrollers"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 201) {
                System.out.println("Microcontrolador cadastrado com sucesso!");
            } else {
                System.err.println("Erro ao cadastrar: " + response.statusCode());
            }
        } catch (Exception e) {
            System.err.println("Erro: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void removeMicrocontroller() {
        try {
            System.out.println("\n--- Remover Microcontrolador ---");
            System.out.print("ID do Microcontrolador: ");
            int id = Integer.parseInt(scanner.nextLine());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(weatherStationManagementUrl + "/microcontrollers/" + id))
                    .DELETE()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                System.out.println("Microcontrolador removido com sucesso!");
            } else {
                System.err.println("Erro ao remover: " + response.statusCode());
            }
        } catch (Exception e) {
            System.err.println("Erro: " + e.getMessage());
        }
    }
    
    public void stop() {
        isRunning = false;
        if (udpSocket != null && !udpSocket.isClosed()) {
            udpSocket.close();
        }
        if (this.scanner != null) {
            this.scanner.close();
        }
        System.out.println("Cliente desconectado.");
    }
}