package com.wind.service;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;

public class RabbitMQService {

    private final String host;
    private final String user;
    private final String pass;
    private final String exchangeName;

    private Connection connection;
    private Channel channel;

    public RabbitMQService(String host, String user, String pass, String exchangeName) {
        this.host = host;
        this.user = user;
        this.pass = pass;
        this.exchangeName = exchangeName;
    }

    public void connect() throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(host);
        factory.setUsername(user);
        factory.setPassword(pass);
        
        int maxRetries = 10;
        for (int i = 0; i < maxRetries; i++) {
            try {
                this.connection = factory.newConnection();
                this.channel = connection.createChannel();
                this.channel.exchangeDeclare(exchangeName, "fanout", true);
                System.out.println("[RabbitMQ] Connected and exchange '" + exchangeName + "' is ready.");
                return;
            } catch (IOException | TimeoutException e) {
                System.err.println("[RabbitMQ] Connection failed (attempt " + (i + 1) + "/" + maxRetries + "): " + e.getMessage());
                if (i < maxRetries - 1) {
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Interrupted while waiting to retry RabbitMQ connection", ie);
                    }
                } else {
                    throw e;
                }
            }
        }
    }

    public void publish(String payload) {
        try {
            if (channel != null && channel.isOpen()) {
                channel.basicPublish(exchangeName, "", null, payload.getBytes(StandardCharsets.UTF_8));
                System.out.println("<- [RABBITMQ] Published to exchange: " + exchangeName);
            } else {
                System.err.println("[RABBITMQ] Cannot publish, channel not available.");
            }
        } catch (IOException e) {
            System.err.println("[RABBITMQ] Error publishing to RabbitMQ: " + e.getMessage());
        }
    }

    public void close() {
        try {
            if (channel != null && channel.isOpen()) channel.close();
            if (connection != null && connection.isOpen()) connection.close();
        } catch (IOException | TimeoutException e) {
            System.err.println("Error disconnecting from RabbitMQ: " + e.getMessage());
        }
    }
}
