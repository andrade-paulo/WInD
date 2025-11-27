package com.wind;

import com.wind.model.LogCSV;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class EngClient {
    private static final String API_KEY = "super-secret-key-123";
    private static final String WEATHER_STATION_SERVICE_NAME = "weather-station";

    private DatagramSocket udpSocket;
    private volatile boolean isRunning = true;
    private final Scanner scanner;
    private final HttpClient httpClient;
    private String gatewayUrl;

    private boolean[] regionsSelected = new boolean[4]; // North, South, East, West

    private LogCSV logCSV = new LogCSV();

    public EngClient() {
        this.scanner = new Scanner(System.in);
        this.httpClient = HttpClient.newHttpClient();
    }

    public static void main(String[] args) {
        System.out.println("WInD - Realtime Client Viewer (UDP Version)");
        System.out.println("===========================");

        EngClient client = new EngClient();
        client.start();
    }

    public void start() {
        try {
            System.out.print("Insira a URL do API Gateway (ex: http://localhost:80): ");
            String inputUrl = scanner.nextLine().trim();
            this.gatewayUrl = inputUrl.isEmpty() ? "http://localhost:80" : inputUrl;

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
            
            // Parse port from address (assuming host:port)
            String[] parts = address.split(":");
            int port = Integer.parseInt(parts[1].subSequence(0, 4).toString());

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
        // Payload format: ID|Location|Pressure|Radiation|Temp|Humidity
        // Example: 1|North|1000.0|500.0|25.0|50.0
        
        String[] parts = payload.split("\\|");
        if (parts.length < 2) return;

        String location = parts[1];
        boolean show = false;

        if (location.equalsIgnoreCase("North") && regionsSelected[0]) show = true;
        else if (location.equalsIgnoreCase("South") && regionsSelected[1]) show = true;
        else if (location.equalsIgnoreCase("East") && regionsSelected[2]) show = true;
        else if (location.equalsIgnoreCase("West") && regionsSelected[3]) show = true;

        if (show) {
            System.out.printf("[DADO RECEBIDO] Local: %s | Dados: %s%n", location, payload);
            logCSV.log("udp/realtime/" + location, payload);
        }
    }

    private void mainMenuLoop() {
        boolean exit = false;
        while (!exit) {
            displayMenu();
            System.out.print("Escolha uma opção: ");
            String choice = scanner.nextLine();

            switch (choice) {
                case "1":
                    regionsSelected = new boolean[]{true, true, true, true}; // Marca todas como selecionadas
                    System.out.println("Visualizando TUDO.");
                    break;
                case "2":
                    regionsSelected = new boolean[]{true, false, false, false}; // Marca Norte como selecionada
                    System.out.println("Visualizando APENAS NORTE.");
                    break;
                case "3":
                    regionsSelected = new boolean[]{false, true, false, false}; // Marca Sul como selecionada
                    System.out.println("Visualizando APENAS SUL.");
                    break;
                case "4":
                    regionsSelected = new boolean[]{false, false, true, false}; // Marca Leste como selecionada
                    System.out.println("Visualizando APENAS LESTE.");
                    break;
                case "5":
                    regionsSelected = new boolean[]{false, false, false, true}; // Marca Oeste como selecionada
                    System.out.println("Visualizando APENAS OESTE.");
                    break;
                case "6":
                    regionsSelected = new boolean[]{false, false, false, false}; // Limpa todas as seleções
                    System.out.println("Visualização PAUSADA.");
                    break;
                case "0":
                    exit = true;
                    System.out.println("Saindo...");
                    break;
                default:
                    System.out.println("Opção inválida. Tente novamente.");
                    break;
            }
        }
    }

    private void displayMenu() {
        System.out.println("\n--- MENU DE VISUALIZAÇÃO ---");
        System.out.println("1. Visualizar TUDO");

        if (regionsSelected[0]) System.out.print("\u001B[35m");
        System.out.println("2. Visualizar Região NORTE\u001B[0m");

        if (regionsSelected[1]) System.out.print("\u001B[35m");
        System.out.println("3. Visualizar Região SUL\u001B[0m");

        if (regionsSelected[2]) System.out.print("\u001B[35m");
        System.out.println("4. Visualizar Região LESTE\u001B[0m");

        if (regionsSelected[3]) System.out.print("\u001B[35m");
        System.out.println("5. Visualizar Região OESTE\u001B[0m");
        
        System.out.println("6. Pausar Visualização");
        System.out.println("0. Sair");
        System.out.println("----------------------------");
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