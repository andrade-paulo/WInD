package com.wind; 

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

public class WeatherStation {

    // Configuração dos Brokers
    private static final String INGRESS_BROKER_URL = "tcp://localhost:1883";
    private static final String INGRESS_CLIENT_ID = "wind-gateway-ingress-subscriber-" + UUID.randomUUID();
    private static final String INGRESS_TOPIC = "raw/data/#";

    private static final String EGRESS_BROKER_URL = "tcp://localhost:1884";
    private static final String EGRESS_CLIENT_ID = "wind-gateway-egress-publisher-" + UUID.randomUUID();

    private static final String RABBITMQ_HOST = "localhost";
    private static final String RABBITMQ_USER = "winduser";
    private static final String RABBITMQ_PASS = "windpass";
    private static final String RABBITMQ_EXCHANGE_NAME = "wind_events_exchange";

    // Conexões
    private IMqttClient ingressClient;
    private IMqttClient egressClient;
    private Connection rabbitConnection;
    private Channel rabbitChannel;

    public static void main(String[] args) {
        System.out.println("WInD - WeatherStation Service");
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

            System.out.println("[INIT] Connecting to Egress MQTT Broker (" + EGRESS_BROKER_URL + ")...");
            connectEgressMqtt();

            System.out.println("[INIT] Connecting to Ingress MQTT Broker (" + INGRESS_BROKER_URL + ")...");
            connectIngressMqtt();

            System.out.println("\nWeatherStation is running and listening for messages. Press Ctrl+C to stop.");

        } catch (Exception e) {
            System.err.println("WeatherStation failed to start: " + e.getMessage());
            e.printStackTrace();
            stop();
        }
    }

    public void stop() {
        try {
            if (ingressClient != null && ingressClient.isConnected()) ingressClient.disconnect();
        } catch (MqttException e) {
            System.err.println("Error disconnecting from Ingress MQTT: " + e.getMessage());
        }
        try {
            if (egressClient != null && egressClient.isConnected()) egressClient.disconnect();
        } catch (MqttException e) {
            System.err.println("Error disconnecting from Egress MQTT: " + e.getMessage());
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

    private void connectEgressMqtt() throws MqttException {
        this.egressClient = new MqttClient(EGRESS_BROKER_URL, EGRESS_CLIENT_ID, new MemoryPersistence());
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        this.egressClient.connect(options);
        System.out.println("[Egress MQTT] Connected.");
    }

    private void connectIngressMqtt() throws MqttException {
        this.ingressClient = new MqttClient(INGRESS_BROKER_URL, INGRESS_CLIENT_ID, new MemoryPersistence());
        
        this.ingressClient.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                System.err.println("[Ingress MQTT] Connection lost! Paho will attempt to reconnect. Cause: " + cause.getMessage());
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                String rawPayload = new String(message.getPayload(), StandardCharsets.UTF_8);
                System.out.println("\n-> [MSG RCV] From topic: " + topic);
                System.out.println("   Payload: " + rawPayload);
                
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

                String egressTopic = topic.replace("raw/data", "formatted/realtime");
                publishToEgress(egressTopic, processedPayload);
                
                publishToRabbitMQ(processedPayload);
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                // Não é usado neste subscriber
            }
        });

        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        this.ingressClient.connect(options);
        this.ingressClient.subscribe(INGRESS_TOPIC, 1);
        System.out.println("[Ingress MQTT] Connected and subscribed to topic '" + INGRESS_TOPIC + "'.");
    }

    private void publishToEgress(String topic, String payload) {
        try {
            if (egressClient != null && egressClient.isConnected()) {
                MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
                message.setQos(1);
                egressClient.publish(topic, message);
                System.out.println("<- [EGRESS] Published to MQTT topic: " + topic);
            } else {
                System.err.println("[EGRESS] Cannot publish, MQTT client not connected.");
            }
        } catch (MqttException e) {
            System.err.println("[EGRESS] Error publishing to MQTT: " + e.getMessage());
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
}