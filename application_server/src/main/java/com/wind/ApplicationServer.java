package com.wind;

import io.javalin.Javalin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.concurrent.TimeoutException;

import com.wind.model.DAO.WeatherDataDAO;
import com.wind.entities.MicrocontrollerEntity;
import com.wind.entities.WeatherData;
import com.wind.model.DAO.LogDAO;
import com.wind.service.ServiceInstancePayload;
import com.wind.service.ServiceRegistrar;

// RabbitMQ
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DeliverCallback;

public class ApplicationServer {
    private static final int HTTP_PORT = 8080; // Porta para a API REST
    private static final String SERVICE_NAME_IN_DISCOVERY = "application-server";

    protected static WeatherDataDAO weatherDataDAO = new WeatherDataDAO();
    protected static LogDAO logDAO = new LogDAO();

    // Configuração do RabbitMQ
    private static final String RABBITMQ_HOST = "localhost";
    private static final String RABBITMQ_USER = "winduser";
    private static final String RABBITMQ_PASS = "windpass";
    private static final String RABBITMQ_EXCHANGE_NAME = "wind_events_exchange";

    public static void main(String[] args) {
        // Inicia o consumidor RabbitMQ
        new Thread(ApplicationServer::startRabbitMQConsumer).start();

        // Inicia o servidor HTTP
        Javalin app = Javalin.create().start(HTTP_PORT);
        System.out.println("Application Server (HTTP) iniciado na porta " + HTTP_PORT);
        registerService();

        // Endpoint de Health Check
        app.get("/health", ctx -> ctx.status(200).result("OK"));

        // Endpoint para SELECT ALL (GET /app/weather)
        app.get("/app/weather", ctx -> {
            WeatherData[] allWeatherData = weatherDataDAO.selectAll();
            if (allWeatherData == null || allWeatherData.length == 0) {
                ctx.status(200).json(new WeatherData[0]); // Retorna array vazio
            } else {
                ctx.status(200).json(allWeatherData);
            }
        });

        // Endpoint para SELECT BY MICROCONTROLLER
        app.get("/app/weather/microcontroller/{id}", ctx -> {
            try {
                int mcId = Integer.parseInt(ctx.pathParam("id"));
                String region = ctx.queryParam("region");

                if (region == null || region.isBlank()) {
                    ctx.status(400).result("O parâmetro 'region' é obrigatório.");
                    return; 
                }

                MicrocontrollerEntity mcEntity = new MicrocontrollerEntity(mcId, region);

                WeatherData[] weatherDataList = weatherDataDAO.selectByMicrocontroller(mcEntity);
                
                if (weatherDataList == null || weatherDataList.length == 0) {
                    ctx.status(200).json(new WeatherData[0]); // Retorna array vazio
                } else {
                    ctx.status(200).json(weatherDataList);
                }
            } catch (NumberFormatException e) {
                ctx.status(400).result("ID do microcontrolador inválido.");
            } catch (Exception e) {
                ctx.status(500).result("Erro interno ao processar a requisição.");
                e.printStackTrace();
            }
        });
    }

    private static void registerService() {
        // "applicationserver" é o nome no Docker Compose.
        String serviceAddress = SERVICE_NAME_IN_DISCOVERY + ":" + HTTP_PORT;
        
        ServiceInstancePayload payload = new ServiceInstancePayload(
            SERVICE_NAME_IN_DISCOVERY,
            SERVICE_NAME_IN_DISCOVERY + "-" + java.util.UUID.randomUUID().toString(),
            serviceAddress,
            "http://" + serviceAddress + "/health"
        );
        
        ServiceRegistrar registrar = new ServiceRegistrar();
        // Atraso para garantir que o service discovery esteja pronto no ambiente Docker
        try { Thread.sleep(10000); } catch (InterruptedException e) { e.printStackTrace(); }
        registrar.register(payload);
    }


    private static void startRabbitMQConsumer() {
        try {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(RABBITMQ_HOST);
            factory.setUsername(RABBITMQ_USER);
            factory.setPassword(RABBITMQ_PASS);
            
            Connection connection = factory.newConnection();
            Channel channel = connection.createChannel();

            channel.exchangeDeclare(RABBITMQ_EXCHANGE_NAME, "fanout", true);
            String queueName = channel.queueDeclare().getQueue();
            channel.queueBind(queueName, RABBITMQ_EXCHANGE_NAME, "");

            System.out.println("[" + new Date() + " RabbitMQ] Consumer ready. Waiting for data...");
            LogDAO.addLog("[RabbitMQ] Consumer ready and waiting for messages.");

            DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
                LogDAO.addLog("[RabbitMQ] Received raw message: '" + message + "'");
                try {
                    // Processa e persiste a mensagem usando o DAO
                    parseAndPersistData(message);
                } catch (Exception e) {
                    System.err.println("[" + new Date() + " RabbitMQ] Failed to process message '" + message + "'. Reason: " + e.getMessage());
                    LogDAO.addLog("[RabbitMQ_ERROR] Failed to process message '" + message + "': " + e.getMessage());
                }
            };
            
            channel.basicConsume(queueName, true, deliverCallback, consumerTag -> {});

        } catch (IOException | TimeoutException e) {
            System.err.println("CRITICAL: Error starting RabbitMQ consumer: " + e.getMessage());
            LogDAO.addLog("[CRITICAL_ERROR] RabbitMQ consumer failed to start: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void parseAndPersistData(String message) {
        String[] parts = message.split("\\|");

        int stationId = Integer.parseInt(parts[0].trim());
        String location = parts[1].trim();
        float pressure = Float.parseFloat(parts[2].trim());
        float radiation = Float.parseFloat(parts[3].trim());
        float temperature = Float.parseFloat(parts[4].trim());
        float humidity = Float.parseFloat(parts[5].trim());

        // Cria as entidades necessárias
        MicrocontrollerEntity microcontroller = new MicrocontrollerEntity(stationId, location);
        WeatherData newData = new WeatherData(microcontroller, pressure, radiation, temperature, humidity);
        
        // Usa o DAO existente para adicionar os dados
        weatherDataDAO.addWeatherData(newData);
        LogDAO.addLog("[DATA_INSERT] New data from RabbitMQ persisted for station ID: " + stationId);
    }
}