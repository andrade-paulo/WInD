package com.wind.CO;

// FILE: MainServerCommunicator.java
import com.wind.entities.WeatherData;
import com.wind.message.Message; // Using your Message class

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.text.ParseException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class ApplicationServerHandler {
    private final String appServerHost;
    private final int appServerPort;

    private Socket socket;

    private ObjectOutputStream oos;
    private ObjectInputStream ois;

    private final Consumer<WeatherData> onDataFromServerCallback;
    private final ExecutorService listenerExecutor = Executors.newSingleThreadExecutor();
    
    private volatile boolean connected = false;

    public ApplicationServerHandler(String host, int port, Consumer<WeatherData> onDataFromServerCallback) {
        this.appServerHost = host;
        this.appServerPort = port;
        this.onDataFromServerCallback = onDataFromServerCallback;
    }

    public void connect() throws IOException {
        this.socket = new Socket(appServerHost, appServerPort);

        this.oos = new ObjectOutputStream(socket.getOutputStream());
        this.oos.flush();
        this.ois = new ObjectInputStream(socket.getInputStream());
        
        connected = true;
        
        System.out.println("[Proxy] Connected to Main Server at " + appServerHost + ":" + appServerPort);

        listenerExecutor.submit(() -> {
            try {
                while (connected && !socket.isClosed()) {
                    Message serverMessage = (Message) ois.readObject();

                    System.out.println("[Proxy] Received Message from Main Server. Instruction: " + serverMessage.getInstrucao());
                    if ("PROPAGATEINSERT".equals(serverMessage.getInstrucao())) {
                        WeatherData data = serverMessage.getWeatherData();

                        if (data != null) {
                            onDataFromServerCallback.accept(data);
                        } else {
                             WeatherData[] dataArray = serverMessage.getWeathers();
                             if(dataArray != null && dataArray.length > 0) {
                                 // Process array if needed, callback might need adjustment
                                 // For now, sending the first element if exists
                                 onDataFromServerCallback.accept(dataArray[0]);
                                 System.out.println("[Proxy] Processed WeatherData[] from server, took first element.");
                             } else {
                                System.out.println("[Proxy] Received Message from server with no weather data.");
                             }
                        }
                    }
                }
            } catch (SocketException e){
                 System.err.println("[Proxy] Main Server connection closed/reset: " + e.getMessage());
            } catch (IOException | ClassNotFoundException e) {
                if (connected) {
                    System.err.println("[Proxy] Error reading from Main Server: " + e.getMessage());
                }
            } catch (ParseException e) {
                System.err.println("[Proxy] Error parsing WeatherData from server message: " + e.getMessage());
            }
            finally {
                disconnect();
            }
        });
    }

    public synchronized void sendWeatherDataToAppServer(WeatherData data) {
        if (oos != null && connected && !socket.isClosed()) {
            try {
                Message messageToServer = new Message(data, "INSERT");
                
                System.out.println("[Proxy -> MainServer] Sending Message with WeatherData from station: " + data.getId());
                
                oos.writeObject(messageToServer);
                oos.flush();
                oos.reset(); 
            } catch (IOException e) {
                System.err.println("[Proxy] IOException sending WeatherData to Main Server: " + e.getMessage());
                disconnect();
            }
        } else {
            System.err.println("[Proxy] Cannot send WeatherData. Not connected to Main Server.");
        }
    }

    public void disconnect() {
        if (!connected) return;

        connected = false;
        System.out.println("[Proxy] Disconnecting from Main Server.");
        
        try {
            if (ois != null) ois.close();
            if (oos != null) oos.close();
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            System.err.println("[Proxy] Error disconnecting from Main Server: " + e.getMessage());
        }

        listenerExecutor.shutdownNow();
    }
}