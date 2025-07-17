package com.wind;

import com.wind.model.LogCSV;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.UUID;

public class EngClient {
    private static final String EGRESS_BROKER_URL = "tcp://localhost:1884";
    private static final String CLIENT_ID = "wind-client-viewer-" + UUID.randomUUID();
    private static final String BASE_TOPIC = "formatted/realtime/";

    private IMqttClient mqttClient;
    private String currentTopic = null;
    private final Scanner scanner;

    private boolean[] regionsSelected = new boolean[4]; // North, South, East, West

    private LogCSV logCSV = new LogCSV();

    public EngClient() {
        this.scanner = new Scanner(System.in);
    }

    public static void main(String[] args) {
        System.out.println("WInD - Realtime Client Viewer");
        System.out.println("===========================");

        EngClient client = new EngClient();
        client.start();
    }

    public void start() {
        try {
            System.out.println("[INIT] Connecting to Egress MQTT Broker (" + EGRESS_BROKER_URL + ")...");
            connect();
            System.out.println("Connection successful.");

            // Loop principal do menu
            mainMenuLoop();

        } catch (MqttException e) {
            System.err.println("Failed to connect to the broker: " + e.getMessage());
            e.printStackTrace();
        } finally {
            stop();
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
                    subscribeToTopic(BASE_TOPIC + "#"); // Wildcard para tudo
                    regionsSelected = new boolean[]{true, true, true, true}; // Marca todas como selecionadas
                    break;
                case "2":
                    subscribeToTopic(BASE_TOPIC + "North/#");
                    regionsSelected = new boolean[]{true, false, false, false}; // Marca Norte como selecionada
                    break;
                case "3":
                    subscribeToTopic(BASE_TOPIC + "South/#");
                    regionsSelected = new boolean[]{false, true, false, false}; // Marca Sul como selecionada
                    break;
                case "4":
                    subscribeToTopic(BASE_TOPIC + "East/#");
                    regionsSelected = new boolean[]{false, false, true, false}; // Marca Leste como selecionada
                    break;
                case "5":
                    subscribeToTopic(BASE_TOPIC + "West/#");
                    regionsSelected = new boolean[]{false, false, false, true}; // Marca Oeste como selecionada
                    break;
                case "6":
                    regionsSelected = new boolean[]{false, false, false, false}; // Limpa todas as seleções
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

        if (regionsSelected[0])
            System.out.print("\u001B[35m");
        System.out.println("2. Visualizar Região NORTE\u001B[0m");

        if (regionsSelected[1])
            System.out.print("\u001B[35m");
        System.out.println("3. Visualizar Região SUL\u001B[0m");

        if (regionsSelected[2])
            System.out.print("\u001B[35m");
        System.out.println("4. Visualizar Região LESTE\u001B[0m");

        if (regionsSelected[3])
            System.out.print("\u001B[35m");
        System.out.println("5. Visualizar Região OESTE\u001B[0m");
        
        System.out.println("0. Sair");
        System.out.println("----------------------------");
        if (currentTopic != null) {
            System.out.println("(Atualmente escutando o tópico: " + currentTopic + ")");
        }
    }

    private void connect() throws MqttException {
        this.mqttClient = new MqttClient(EGRESS_BROKER_URL, CLIENT_ID, new MemoryPersistence());

        this.mqttClient.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                System.err.println("-> CONEXÃO PERDIDA! Tentando reconectar...");
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
                // Imprime a mensagem formatada para fácil leitura
                System.out.printf("[DADO RECEBIDO] Tópico: %s | Dados: %s%n", topic, payload);
                logCSV.log(topic, payload); // Registra os dados no CSV
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                // Não aplicável para um cliente que só se inscreve
            }
        });

        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        this.mqttClient.connect(options);
    }

    private void subscribeToTopic(String newTopic) {
        if (mqttClient == null || !mqttClient.isConnected()) {
            System.err.println("Não é possível se inscrever, cliente não está conectado.");
            return;
        }

        try {
            // Se já estiver inscrito em um tópico, cancela a inscrição primeiro
            if (this.currentTopic != null && !this.currentTopic.equals(newTopic)) {
                System.out.println("Cancelando inscrição do tópico antigo: " + this.currentTopic);
                mqttClient.unsubscribe(this.currentTopic);
            }

            // Se inscreve no novo tópico
            if (!newTopic.equals(this.currentTopic)) {
                System.out.println("Inscrevendo-se no novo tópico: " + newTopic);
                mqttClient.subscribe(newTopic, 1); // QoS 1
                this.currentTopic = newTopic;
                System.out.println("Agora você está visualizando os dados. Novas mensagens aparecerão abaixo.");
            } else {
                System.out.println("Você já está visualizando este tópico.");
            }
        } catch (MqttException e) {
            System.err.println("Erro ao tentar se inscrever no tópico " + newTopic + ": " + e.getMessage());
        }
    }
    
    public void stop() {
        System.out.println("Desconectando o cliente...");
        if (this.mqttClient != null && this.mqttClient.isConnected()) {
            try {
                this.mqttClient.disconnect();
                this.mqttClient.close();
            } catch (MqttException e) {
                System.err.println("Erro ao desconectar: " + e.getMessage());
            }
        }
        this.scanner.close();
        System.out.println("Cliente desconectado.");
    }
}