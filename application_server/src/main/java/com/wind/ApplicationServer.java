package com.wind;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;

import com.wind.entities.MicrocontrollerEntity;
import com.wind.entities.WeatherData;
import com.wind.entities.ClientEntity;
import com.wind.model.DAO.LogDAO;
import com.wind.model.DAO.WeatherDataDAO;
import com.wind.model.DAO.ClientDAO;
import com.wind.service.ServiceInstancePayload;
import com.wind.service.ServiceRegistrar;
import com.wind.security.RSA;

import io.javalin.Javalin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Date;
import java.util.concurrent.TimeoutException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ApplicationServer {

    private static final int HTTP_PORT = 8080;
    private static final String SERVICE_NAME_IN_DISCOVERY = "application-server";

    private static final ServiceRegistrar serviceRegistrar = new ServiceRegistrar();
    private static ServiceInstancePayload servicePayload;

    // Restaurando os DAOs conforme o projeto original
    private static final WeatherDataDAO weatherDataDAO = new WeatherDataDAO();
    private static final ClientDAO clientDAO = new ClientDAO();
    @SuppressWarnings("unused")
    private static final LogDAO logDAO = new LogDAO();

    // Configuração do RabbitMQ para um exchange FANOUT
    private static final String RABBITMQ_HOST = "wind-internal-bus";
    private static final String RABBITMQ_USER = "winduser";
    private static final String RABBITMQ_PASS = "windpass";
    private static final String RABBITMQ_EXCHANGE_NAME = "wind_events_exchange";

    private static PrivateKey privateKey;
    private static PublicKey publicKey;
    // Map to store AES keys for each session/client (simplified for this context)
    // In a real scenario, this should be associated with a session token
    private static final Map<String, byte[]> sessionKeys = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        try {
            KeyPair keyPair = RSA.generateKeyPair();
            privateKey = keyPair.getPrivate();
            publicKey = keyPair.getPublic();
        } catch (Exception e) {
            System.err.println("Erro ao gerar par de chaves RSA: " + e.getMessage());
            System.exit(1);
        }

        Javalin app = Javalin.create().start("0.0.0.0", HTTP_PORT);
        System.out.println("Application Server (HTTP) iniciado na porta " + HTTP_PORT);

        boolean registered = registerService();
        if (registered) {
            // Só inicia o heartbeat se o registro inicial foi bem-sucedido
            serviceRegistrar.startHeartbeat();
            // Adiciona um gancho de desligamento para parar o heartbeat de forma limpa
            Runtime.getRuntime().addShutdownHook(new Thread(serviceRegistrar::shutdown));
        } else {
            System.err.println("Aplicação iniciando em modo degradado pois não conseguiu se registrar no Service Discovery.");
        }

        app.get("/health", ctx -> ctx.status(200).result("OK"));

        app.get("/app/security/public-key", ctx -> {
            ctx.result(RSA.publicKeyToBase64(publicKey));
        });

        app.post("/app/security/handshake", ctx -> {
            String encryptedAesKey = ctx.body();
            
            try {
                byte[] aesKey = RSA.decrypt(encryptedAesKey, privateKey);
                
                // Client/EngClient Handshake (Session based)
                String sessionId = ctx.ip(); 
                sessionKeys.put(sessionId, aesKey);
                LogDAO.addLog("[HANDSHAKE] Handshake realizado com sucesso para " + sessionId);
                
                ctx.status(200).result("Handshake successful");
            } catch (Exception e) {
                LogDAO.addLog("[HANDSHAKE ERROR] Falha no handshake: " + e.getMessage());
                ctx.status(500).result("Handshake failed");
            }
        });

        app.post("/app/auth/login", ctx -> {
            ClientEntity credentials = ctx.bodyAsClass(ClientEntity.class);
            ClientEntity client = clientDAO.authenticate(credentials.getUsername(), credentials.getPassword());
            if (client != null) {
                ctx.status(200).json(client);
            } else {
                ctx.status(401).result("Credenciais inválidas");
            }
        });

        app.post("/app/auth/register", ctx -> {
            ClientEntity credentials = ctx.bodyAsClass(ClientEntity.class);
            if (clientDAO.exists(credentials.getUsername())) {
                ctx.status(409).result("Usuário já existe");
            } else {
                clientDAO.addClient(credentials);
                ctx.status(201).result("Usuário criado com sucesso");
            }
        });

        app.get("/app/weather", ctx -> {
            LogDAO.addLog("[HTTP_REQUEST] Recebida requisição para SELECT ALL.");
            WeatherData[] allWeatherData = weatherDataDAO.selectAll();
            ctx.status(200).json(allWeatherData != null ? allWeatherData : new WeatherData[0]);
        });

        app.get("/app/weather/microcontroller/{id}", ctx -> {
            try {
                int mcId = Integer.parseInt(ctx.pathParam("id"));
                String region = ctx.queryParam("region");

                if (region == null || region.isBlank()) {
                    ctx.status(400).result("O parâmetro 'region' é obrigatório.");
                    return;
                }
                LogDAO.addLog("[HTTP_REQUEST] Recebida requisição para SELECT BY MC: id=" + mcId + ", region=" + region);

                MicrocontrollerEntity mcEntity = new MicrocontrollerEntity(mcId, region);
                
                WeatherData[] weatherDataList = weatherDataDAO.selectByMicrocontroller(mcEntity);
                ctx.status(200).json(weatherDataList != null ? weatherDataList : new WeatherData[0]);

            } catch (NumberFormatException e) {
                ctx.status(400).result("ID do microcontrolador inválido.");
            }
        });

        Thread rabbitMqThread = new Thread(ApplicationServer::startRabbitMQConsumer, "RabbitMQ-Consumer-Thread");
        rabbitMqThread.setDaemon(true);
        rabbitMqThread.start();
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
        try {
            String[] parts = message.split("\\|");
            if (parts.length < 6) return; // Ignora mensagens malformadas

            int stationId = Integer.parseInt(parts[0].trim());
            String location = parts[1].trim();
            float pressure = Float.parseFloat(parts[2].trim());
            float radiation = Float.parseFloat(parts[3].trim());
            float temperature = Float.parseFloat(parts[4].trim());
            float humidity = Float.parseFloat(parts[5].trim());

            MicrocontrollerEntity microcontroller = new MicrocontrollerEntity(stationId, location);
            WeatherData newData = new WeatherData(microcontroller, pressure, radiation, temperature, humidity);

            weatherDataDAO.addWeatherData(newData);
            LogDAO.addLog("[DATA_INSERT] Novos dados do RabbitMQ persistidos para a estação ID: " + stationId);
        } catch (Exception e) {
            System.err.println("Erro no parsing da mensagem: " + e.getMessage());
            LogDAO.addLog("[PARSE_ERROR] Mensagem inválida recebida do RabbitMQ: " + message);
        }
    }

    /**
     * Registra esta instância do serviço no Service Discovery.
     */
    private static boolean registerService() {
        String serviceAddress = "application-server:" + HTTP_PORT;
        
        servicePayload = new ServiceInstancePayload(
            SERVICE_NAME_IN_DISCOVERY,
            SERVICE_NAME_IN_DISCOVERY + "-" + java.util.UUID.randomUUID().toString(),
            serviceAddress,
            "http://" + serviceAddress + "/health"
        );
        
        return serviceRegistrar.register(servicePayload);
    }
}