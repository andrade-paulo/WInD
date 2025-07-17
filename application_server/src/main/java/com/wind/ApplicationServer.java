package com.wind;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;

import com.wind.model.DAO.WeatherDataDAO;
import com.wind.model.DAO.LogDAO;
import com.wind.entities.MicrocontrollerEntity;
import com.wind.entities.WeatherData;
import com.wind.message.Message;

// RabbitMQ
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DeliverCallback;

public class ApplicationServer {
    private static int port = 54321;

    // A camada de acesso a dados (DAO) é mantida
    protected static WeatherDataDAO WeatherDataDAO = new WeatherDataDAO();
    protected static LogDAO logDAO = new LogDAO();

    private static final List<ApplicationHandler> activeHandlers = new CopyOnWriteArrayList<>();

    // Configuração do RabbitMQ
    private static final String RABBITMQ_HOST = "localhost";
    private static final String RABBITMQ_USER = "winduser";
    private static final String RABBITMQ_PASS = "windpass";
    private static final String RABBITMQ_EXCHANGE_NAME = "wind_events_exchange";

    public static void main(String[] args) {
        System.out.println("");

        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        }

        Thread rabbitMqThread = new Thread(ApplicationServer::startRabbitMQConsumer, "RabbitMQ-Consumer-Thread");
        rabbitMqThread.setDaemon(true);
        rabbitMqThread.start();

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("\nApplication server running on port: " + port);

            while (true) {
                Socket socket = serverSocket.accept();
                ApplicationHandler handler = new ApplicationHandler(socket);
                new Thread(handler).start();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
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
        WeatherDataDAO.addWeatherData(newData);
        LogDAO.addLog("[DATA_INSERT] New data from RabbitMQ persisted for station ID: " + stationId);
    }


    // Connection handler for the proxy connections
    private static class ApplicationHandler implements Runnable {
        private Socket socket;
        private boolean conexao = true;
        @SuppressWarnings("unused")  // Por algum motivo tá acusando unused
        private ObjectOutputStream objectOutputStream;
        private String socketAddress;

        public ApplicationHandler(Socket socket) {
            this.socket = socket;
            this.socketAddress = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
            System.out.println("\n-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-");
            System.out.println("Hello, " + this.socketAddress);
            System.out.println("-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-");
            LogDAO.addLog("[CONNECTION] Conexão estabelecida com " + this.socketAddress);
        }

        @Override
        public void run() {
            try (ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
                 ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {

                this.objectOutputStream = out;
                activeHandlers.add(this);

                while (conexao) {
                    Message message = (Message) in.readObject();
                    String instruction = message.getInstrucao();
                    LogDAO.addLog("[MESSAGE] " + this.socketAddress + " requisitou " + instruction);

                    try {
                        switch (instruction) {
                            case "SELECTALL":
                                handleSelectAll(out);
                                break;
                            case "SELECTBYMC":
                                handleSelectByMicrocontroller(message, out);
                                break;
                            default:
                                System.out.println("Unknown instruction : " + instruction + " from " + this.socketAddress);
                                LogDAO.addLog("[WARNING] Unknown instruction: " + instruction + " from " + this.socketAddress);
                                break;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } catch (EOFException e) {
                // ...
            } catch (IOException | ClassNotFoundException e) {
                // ...
            } finally {
                activeHandlers.remove(this);
            }
        }

        private void handleSelectAll(ObjectOutputStream out) throws IOException {
            WeatherData[] allWeatherData = WeatherDataDAO.selectAll();
            Message reply;

            if (allWeatherData == null || allWeatherData.length == 0) {
                 reply = new Message(new WeatherData[0], "EMPTY_SELECT_ALL"); // Send empty array with specific status
            } else {
                // reply = new Message(allWeatherData, "REPLY"); // Original
                reply = new Message(allWeatherData, "SUCCESS_SELECT_ALL");
            }
            
            out.writeObject(reply);
            out.flush();
        }

        private void handleSelectByMicrocontroller(Message message, ObjectOutputStream out) throws IOException, ParseException {
            WeatherData[] weatherDataList = WeatherDataDAO.selectByMicrocontroller(message.getMicrocontrollerEntity());
            Message reply;

            if (weatherDataList == null || weatherDataList.length == 0) {
                reply = new Message(new WeatherData[0], "EMPTY_SELECT_BY_MC"); // Send empty array
            } else {
                reply = new Message(weatherDataList, "SUCCESS_SELECT_BY_MC");
            }

            System.out.println(reply.getWeathers().length + " weather data found for microcontroller");

            out.writeObject(reply);
            out.flush();
        }
    }
}