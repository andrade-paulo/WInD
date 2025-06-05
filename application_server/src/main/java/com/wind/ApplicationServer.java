package com.wind;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.ParseException;
import java.util.List; // Added
import java.util.concurrent.CopyOnWriteArrayList; // Added

import com.wind.model.DAO.WeatherDataDAO;
import com.wind.model.DAO.LogDAO;
import com.wind.entities.WeatherData;
import com.wind.message.Message;

public class ApplicationServer {
    private static int port = 54321;

    protected static WeatherDataDAO WeatherDataDAO = new WeatherDataDAO();
    protected static LogDAO logDAO = new LogDAO();

    // List to keep track of all active client handlers (proxies)
    private static final List<ApplicationHandler> activeHandlers = new CopyOnWriteArrayList<>(); // Thread-safe list

    public static void main(String[] args) {
        System.out.println("\r\n" + //
                            "===========================================================\r\n" + //
                            "                  _       ______      ____                 \r\n" + //
                            "                 | |     / /  _/___  / __ \\               \r\n" + //
                            "                 | | /| / // // __ \\/ / / /               \r\n" + //
                            "                 | |/ |/ // // / / / /_/ /                 \r\n" + //
                            "                 |__/|__/___/_/ /_/_____/                  \r\n" + //
                            "                                                           \r\n" + //
                            "           _               ___                             \r\n" + //
                            "          /_\\  _ __ _ __  / __| ___ _ ___ _____ _ _       \r\n" + //
                            "         / _ \\| '_ \\ '_ \\ \\__ \\/ -_) '_\\ V / -_) '_| \r\n" + //
                            "        /_/ \\_\\ .__/ .__/ |___/\\___|_|  \\_/\\___|_|    \r\n" + //
                            "              |_|  |_|                                     \r\n" + //
                            "                                                           \r\n" + //
                            "===========================================================\r\n");


        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        }

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("\nApplication server running on port: " + port);

            while (true) {
                Socket socket = serverSocket.accept(); // Aguarda conexão do proxy server
                ApplicationHandler handler = new ApplicationHandler(socket); // Create handler
                // activeHandlers.add(handler); // Add handler to the list (Moved to run() for safer oos initialization)
                new Thread(handler).start();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Method to broadcast a message to all connected proxies
    private static void broadcastWeatherData(WeatherData weatherData, ApplicationHandler originatingHandler) {
        LogDAO.addLog("[BROADCAST] Broadcasting new WeatherData ID: " + weatherData.getId() + " from " + originatingHandler.getSocketAddress());
        Message broadcastMessage = new Message(weatherData, "PROPAGATE_INSERT"); // New instruction for proxies

        for (ApplicationHandler handler : activeHandlers) {
            try {
                handler.sendMessage(broadcastMessage);
                LogDAO.addLog("[BROADCAST] Sent WeatherData ID: " + weatherData.getId() + " to proxy " + handler.getSocketAddress());
            } catch (IOException e) {
                LogDAO.addLog("[BROADCAST_ERROR] Failed to send WeatherData ID: " + weatherData.getId() + " to proxy " + handler.getSocketAddress() + ". Error: " + e.getMessage());
                // activeHandlers.remove(handler);
            }
        }
    }

    private static class ApplicationHandler implements Runnable {
        private Socket socket;
        private boolean conexao = true;
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

        public String getSocketAddress() {
            return this.socketAddress;
        }
        
        public void sendMessage(Message message) throws IOException {
            if (objectOutputStream != null) {
                objectOutputStream.writeObject(message);
                objectOutputStream.flush();
            } else {
                throw new IOException("Output stream is not initialized or closed for " + this.socketAddress);
            }
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
                            case "INSERT":
                                handleInsert(message, out); // Pass 'out' for direct reply
                                break;
                            case "SELECT":
                                handleSelect(message, out);
                                break;
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
                        System.err.println("Error processing instruction " + instruction + " from " + this.socketAddress + ": " + e);
                        LogDAO.addLog("[ERROR] Processing " + instruction + " from " + this.socketAddress + ": " + e.getMessage());
                         try {
                            Message errorReply = new Message("SERVER_ERROR");
                            out.writeObject(errorReply);
                            out.flush();
                        } catch (IOException ex) {
                            System.err.println("Failed to send error reply to " + this.socketAddress + ": " + ex.getMessage());
                        }
                        e.printStackTrace(); // For server-side debugging
                    }
                }

            } catch (EOFException e) {
                System.out.println("Connection closed by client: " + this.socketAddress);
                LogDAO.addLog("[CONNECTION] Connection closed by client: " + this.socketAddress + ". EOFException.");
            } catch (IOException e) {
                System.out.println("IOException with client " + this.socketAddress + ": " + e.getMessage());
                LogDAO.addLog("[CONNECTION_ERROR] IOException with " + this.socketAddress + ": " + e.getMessage());
                e.printStackTrace();
            }
            catch (ClassNotFoundException e) {
                System.err.println("ClassNotFoundException from " + this.socketAddress + ": " + e.getMessage());
                LogDAO.addLog("[ERROR] ClassNotFoundException from " + this.socketAddress + ": " + e.getMessage());
                e.printStackTrace();
            } finally {
                activeHandlers.remove(this); // Remove this handler from the list
                this.objectOutputStream = null; // Clear the reference

                LogDAO.addLog("[CONNECTION] Conexão finalizada com " + this.socketAddress + ". Active handlers: " + activeHandlers.size());
                System.out.println("Connection finalized with " + this.socketAddress + ". Active handlers: " + activeHandlers.size());

                try {
                    if (socket != null && !socket.isClosed()) {
                        socket.close();
                    }
                } catch (IOException e) {
                    LogDAO.addLog("[ERROR] Failed to close socket for " + this.socketAddress + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }

        private void handleInsert(Message message, ObjectOutputStream out) throws IOException, ParseException {
            Message reply;
            WeatherData newData = message.getWeatherData();
            try{
                WeatherDataDAO.addWeatherData(newData);
                reply = new Message("SUCCESS");
                out.writeObject(reply);
                out.flush();
                
                // Broadcast the new WeatherData to all connected proxies
                ApplicationServer.broadcastWeatherData(newData, this);

            } catch (Exception e) {
                System.err.println("Error in handleInsert from " + this.socketAddress + ": " + e.getMessage());
                LogDAO.addLog("[ERROR] INSERT failed for " + this.socketAddress + ": " + e.getMessage());
                e.printStackTrace();
            }
        }

        private void handleSelect(Message message, ObjectOutputStream out) throws IOException {
            WeatherData weatherData = WeatherDataDAO.getWeatherData(message.getCodigo());
            Message reply;
            if (weatherData == null) {
                reply = new Message("NOTFOUND");
                System.out.println("WeatherData not found for code: " + message.getCodigo() + " (requested by " + this.socketAddress + ")");
            } else {
                reply = new Message(weatherData, "SUCCESS_SELECT_SINGLE");
            }
            out.writeObject(reply);
            out.flush();
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

        private void handleSelectByMicrocontroller(Message message, ObjectOutputStream out) throws IOException {
            WeatherData[] weatherDataList = WeatherDataDAO.listarOS(message.getMicrocontrollerEntity());
            Message reply;
            if (weatherDataList == null || weatherDataList.length == 0) {
                reply = new Message(new WeatherData[0], "EMPTY_SELECT_BY_MC"); // Send empty array
            } else {
                // reply = new Message(weatherDataList, "REPLY"); // Original
                reply = new Message(weatherDataList, "SUCCESS_SELECT_BY_MC");
            }
            out.writeObject(reply);
            out.flush();
        }
    }
}