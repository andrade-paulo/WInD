package com.wind;

import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.util.Date;
import java.util.Locale;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.UUID;


public class Microcontroller {

    private final int id;
    private final String location;
    private final String brokerUrl;
    private final String topic;

    private final Character prefix;
    private final Character suffix;
    private final Character separator;

    private volatile boolean isRunning = true;
    private final Random random;
    private IMqttClient mqttClient;

    private static final int RECONNECT_DELAY_MS = 5000;

    public Microcontroller(int id, String location, String brokerHost, int brokerPort, Character prefix, Character suffix, Character separator) {
        this.id = id;
        // Valida a localização para garantir que seja uma das opções esperadas
        this.location = location;
        this.brokerUrl = String.format("tcp://%s:%d", brokerHost, brokerPort);
        this.topic = String.format("raw/data/%s/%d", this.location, this.id);
        
        this.prefix = prefix != null ? prefix : '\0';
        this.suffix = suffix != null ? suffix : '\0';
        this.separator = separator != null ? separator : ';';

        this.random = new Random();
    }

    public static void main(String[] args) {
        System.out.println("WInD - Microcontroller Simulation (MQTT Version)");

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
            Character prefix = scanner.nextLine().charAt(0);
            System.out.print("Suffix (optional): ");
            Character suffix = scanner.nextLine().charAt(0);
            System.out.print("Separator (default ';'): ");
            Character separator = scanner.nextLine().charAt(0);

            // Broker Configuration
            System.out.print("Broker Host (default: localhost): ");
            String brokerHost = scanner.nextLine().trim();
            if (brokerHost.isEmpty()) {
                brokerHost = "localhost";
            }

            System.out.print("Broker Port (default: 1883): ");
            String portStr = scanner.nextLine().trim();
            int brokerPort = portStr.isEmpty() ? 1883 : Integer.parseInt(portStr);

            System.out.println("===================================================");
            System.out.println("        Microcontroller Simulation Starting");
            System.out.println("  Targeting Broker at " + brokerHost + ":" + brokerPort);
            System.out.println("  Publishing to Topic: raw/data/" + location + "/" + id);
            System.out.println("===================================================");

            Microcontroller mc = new Microcontroller(id, location, brokerHost, brokerPort, prefix, suffix, separator);
            Thread mcThread = new Thread(mc::startSendingData, "MQTT-MC-" + id);
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
        if (mqttClient != null && mqttClient.isConnected()) {
            return true;
        }
        try {
            // Gera um Client ID único para evitar conflitos no broker
            String clientId = "wind-mc-" + id + "-" + UUID.randomUUID().toString().substring(0, 8);
            mqttClient = new MqttClient(brokerUrl, clientId);

            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true); // Deixa a biblioteca Paho cuidar da reconexão
            options.setCleanSession(true);
            options.setConnectionTimeout(10); // Timeout de 10 segundos

            System.out.println("[" + new Date() + " MC-" + id + "] Attempting to connect to MQTT broker at " + brokerUrl + "...");
            mqttClient.connect(options);
            System.out.println("[" + new Date() + " MC-" + id + "] Successfully connected to MQTT broker.");
            return true;

        } catch (MqttException e) {
            System.err.println("[" + new Date() + " MC-" + id + "] Error connecting to MQTT broker: " + e.getMessage());
            return false;
        }
    }

    private void disconnect() {
        if (mqttClient != null && mqttClient.isConnected()) {
            try {
                System.out.println("[" + new Date() + " MC-" + id + "] Disconnecting from MQTT broker...");
                mqttClient.disconnect();
                mqttClient.close();
                System.out.println("[" + new Date() + " MC-" + id + "] Disconnected.");
            } catch (MqttException e) {
                System.err.println("[" + new Date() + " MC-" + id + "] Error while disconnecting: " + e.getMessage());
            }
        }
    }

    public void startSendingData() {
        if (!connect()) {
            // Se a primeira conexão falhar, o loop principal tentará reconectar
            System.err.println("[" + new Date() + " MC-" + id + "] Initial connection failed. Will retry in background.");
        }

        while (isRunning && !Thread.currentThread().isInterrupted()) {
            if (mqttClient == null || !mqttClient.isConnected()) {
                 System.err.println("[" + new Date() + " MC-" + id + "] Connection lost. Paho will attempt to reconnect automatically. Waiting...");
                try {
                    // Espera um pouco para a reconexão automática funcionar
                    TimeUnit.MILLISECONDS.sleep(RECONNECT_DELAY_MS);
                    continue; // Volta para o início do loop e checa a conexão novamente
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            // Gera dados climáticos aleatórios
            float pressure = 950.0f + random.nextFloat() * (1050.0f - 950.0f);
            float radiation = random.nextFloat() * 1400.0f;
            float temperature = 253.15f + random.nextFloat() * (323.15f - 253.15f);
            float humidity = random.nextFloat() * 100.0f;

            String dataString = String.format(Locale.US, "%c%d%c%s%c%.2f%c%.2f%c%.2f%c%.2f%c",
                this.prefix, this.id, this.separator, location, this.separator, pressure, this.separator, radiation, this.separator, temperature, this.separator, humidity, this.suffix);

            MqttMessage message = new MqttMessage(dataString.getBytes());
            message.setQos(1); // Qualidade de Serviço 1: "pelo menos uma vez"

            try {
                mqttClient.publish(this.topic, message);
                System.out.println("[" + new Date() + " MC-" + id + "] Published to topic '" + this.topic + "': " + dataString);

            } catch (MqttException e) {
                System.err.println("[" + new Date() + " MC-" + id + "] Error publishing message: " + e.getMessage());
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