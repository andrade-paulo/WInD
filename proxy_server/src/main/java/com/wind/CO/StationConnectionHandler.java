package com.wind.CO;

// FILE: StationConnectionHandler.java
import com.wind.entities.WeatherData;
import com.wind.message.Message; // Using your Message class
import com.wind.model.DAO.WeatherDataDAO;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.text.ParseException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StationConnectionHandler {
    private final int stationPort;
    private final WeatherDataDAO weatherDataDAO;
    private final ExecutorService stationExecutor;
    private volatile boolean running = true;
    private ServerSocket serverSocket;


    public StationConnectionHandler(int port, WeatherDataDAO dao) {
        this.stationPort = port;
        this.weatherDataDAO = dao;
        this.stationExecutor = Executors.newCachedThreadPool();
    }

    public void startListening() {
        System.out.println("[Proxy] Listening for Weather Stations on port " + stationPort);
        try {
            serverSocket = new ServerSocket(stationPort);
            while (running && !serverSocket.isClosed()) {
                Socket stationSocket = serverSocket.accept();
                stationExecutor.submit(() -> handleStation(stationSocket));
            }
        } catch (SocketException e) {
            if (running) { // Avoid error if stopListening was called
                System.err.println("[Proxy] ServerSocket exception in StationConnectionHandler (possibly closed): " + e.getMessage());
            }
        } catch (IOException e) {
            if (running) {
                System.err.println("[Proxy] IOException in StationConnectionHandler: " + e.getMessage());
            }
        } finally {
            stopListening(); // Ensure resources are cleaned up if loop exits unexpectedly
        }
    }

    private void handleStation(Socket stationSocket) {
        String stationInfo = stationSocket.getInetAddress().getHostAddress() + ":" + stationSocket.getPort();
        System.out.println("[Proxy] Weather Station connected: " + stationInfo);

        try (ObjectInputStream ois = new ObjectInputStream(stationSocket.getInputStream());
             ObjectOutputStream oos = new ObjectOutputStream(stationSocket.getOutputStream())) { 

            oos.flush();

            while (running && !stationSocket.isClosed()) {
                Message stationMessage = (Message) ois.readObject(); // Read Message object
                System.out.println("[Proxy] Received Message from Station (" + stationInfo + "). Instruction: " + stationMessage.getInstrucao());


                if ("INSERT".equals(stationMessage.getInstrucao())) {
                    System.out.println("[Proxy] Processing WeatherData from station: " + stationInfo);

                    WeatherData data = stationMessage.getWeatherData();
                    weatherDataDAO.processStationData(data);

                    oos.writeObject(new Message("INSERT_OK")); // Acknowledge successful insert
                    oos.flush();
                } else {
                    System.out.println("[Proxy] Unknown instruction from station: " + stationMessage.getInstrucao());
                }
            }
        } catch (EOFException e) {
            System.out.println("[Proxy] Station (" + stationInfo + ") disconnected.");
        } catch (SocketException e) {
            System.out.println("[Proxy] SocketException with station (" + stationInfo + "): " + e.getMessage());
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("[Proxy] Error handling station " + stationInfo + ": " + e.getMessage());
        } catch (ParseException e) {
            System.err.println("[Proxy] Error parsing WeatherData from station message " + stationInfo + ": " + e.getMessage());
        }
        finally {
            try {
                if (!stationSocket.isClosed()) stationSocket.close();
            } catch (IOException e) {
                System.err.println("[Proxy] Error closing station socket " + stationInfo + ": ");
                e.printStackTrace();
            }
            System.out.println("[Proxy] Finished handling Weather Station: " + stationInfo);
        }
    }

    public void stopListening() {
        running = false;
        System.out.println("[Proxy] Stopping StationConnectionHandler...");
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close(); // This will interrupt serverSocket.accept()
            }
        } catch (IOException e) {
            System.err.println("[Proxy] Error closing server socket: " + e.getMessage());
        }
        stationExecutor.shutdownNow();
        System.out.println("[Proxy] StationConnectionHandler stopped.");
    }
}