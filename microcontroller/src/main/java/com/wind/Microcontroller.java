package com.wind;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.io.IOException;

import java.util.Date;
import java.util.Locale;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;


public class Microcontroller {

    private final int id;
    private final String location;
    private final String serverHost;
    private final int serverPort;

    private final Character prefix;
    private final Character suffix;
    private final Character separator;

    private volatile boolean isRunning = true;
    private final Random random;
    private DatagramSocket socket;

    public Microcontroller(int id, String location, String serverHost, int serverPort, Character prefix, Character suffix, Character separator) {
        this.id = id;
        // Valida a localização para garantir que seja uma das opções esperadas
        this.location = location;
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        
        this.prefix = prefix != null ? prefix : '\0';
        this.suffix = suffix != null ? suffix : '\0';
        this.separator = separator != null ? separator : ';';

        this.random = new Random();
    }

    public static void main(String[] args) {
        System.out.println("WInD - Microcontroller Simulation (UDP Version)");

        try (Scanner scanner = new Scanner(System.in)) {
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

            System.out.println("===================================================");
            System.out.println("        Microcontroller Simulation Starting");
            System.out.println("  Targeting UDP Server at " + serverHost + ":" + serverPort);
            System.out.println("===================================================");

            Microcontroller mc = new Microcontroller(id, location, serverHost, serverPort, prefix, suffix, separator);
            Thread mcThread = new Thread(mc::startSendingData, "UDP-MC-" + id);
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
                socket = new DatagramSocket();
                System.out.println("[" + new Date() + " MC-" + id + "] UDP Socket created.");
            }
            return true;
        } catch (Exception e) {
            System.err.println("[" + new Date() + " MC-" + id + "] Error creating UDP socket: " + e.getMessage());
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

    public void startSendingData() {
        if (!connect()) {
            System.err.println("[" + new Date() + " MC-" + id + "] Initial socket creation failed.");
            return;
        }

        InetAddress address;
        try {
            address = InetAddress.getByName(serverHost);
        } catch (Exception e) {
             System.err.println("[" + new Date() + " MC-" + id + "] Unknown host: " + serverHost);
             return;
        }

        while (isRunning && !Thread.currentThread().isInterrupted()) {
            // Gera dados climáticos aleatórios
            float pressure = 950.0f + random.nextFloat() * (1050.0f - 950.0f);
            float radiation = random.nextFloat() * 1400.0f;
            float temperature = 253.15f + random.nextFloat() * (323.15f - 253.15f);
            float humidity = random.nextFloat() * 100.0f;

            String dataString = String.format(Locale.US, "%c%d%c%s%c%.2f%c%.2f%c%.2f%c%.2f%c",
                this.prefix, this.id, this.separator, location, this.separator, pressure, this.separator, radiation, this.separator, temperature, this.separator, humidity, this.suffix);

            byte[] buffer = dataString.getBytes();
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, serverPort);

            try {
                socket.send(packet);
                System.out.println("[" + new Date() + " MC-" + id + "] Sent UDP packet to " + serverHost + ":" + serverPort + " : " + dataString);

            } catch (IOException e) {
                System.err.println("[" + new Date() + " MC-" + id + "] Error sending packet: " + e.getMessage());
            }
            
            int sleepTimeMs = 2000 + random.nextInt(3001); // 2 a 5 segundos
            try {
                TimeUnit.MILLISECONDS.sleep(sleepTimeMs);
            } catch (InterruptedException e) {
                System.out.println("[" + new Date() + " MC-" + id + "] Send loop interrupted.");
                isRunning = false; // Sinaliza para sair do loop
                Thread.currentThread().interrupt(); // Preserva o status de interrupção
            }
        }
        disconnect();
        System.out.println("[" + new Date() + " MC-" + id + "] Stopped sending data.");
    }

    public void stopSendingData() {
        this.isRunning = false;
    }
}