package com.wind; // Or your appropriate package

import java.io.IOException;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import java.util.Scanner;

public class Microcontroller {

    private final int id;
    private final String location;

    private final Character prefix;
    private final Character suffix;
    private final Character separator;

    private final String weatherStationHost;
    private final int weatherStationPort;

    private volatile boolean isRunning = true;
    
    private Random random;
    private Socket socket;
    private PrintWriter out;

    private static final int RECONNECT_DELAY_MS = 3000;

    public Microcontroller(int id, String location, String weatherStationHost, int weatherStationPort, Character prefix, Character suffix, Character separator) {
        this.id = id;
        this.location = location;
        
        this.weatherStationHost = weatherStationHost;
        this.weatherStationPort = weatherStationPort;

        this.prefix = prefix != null ? prefix : '\0';
        this.suffix = suffix != null ? suffix : '\0';
        this.separator = separator != null ? separator : ';';

        this.random = new Random();
    }


    public static void main(String[] args) {
        System.out.println("WING - Microcontroller Simulation");

        Scanner scanner = new Scanner(System.in);
        System.out.print("ID: ");
        int id = scanner.nextInt();
        scanner.nextLine(); // Consume newline

        System.out.print("Location: ");
        String location = scanner.nextLine().trim();
        
        System.out.print("WeatherStation Host: ");
        String weatherStationHost = scanner.nextLine().trim();

        System.out.print("WeatherStation Port: ");
        int weatherStationPort = scanner.nextInt();
        scanner.nextLine(); // Consume newline

        System.out.print("Prefix (optional): ");
        Character prefix = scanner.nextLine().charAt(0);

        System.out.print("Suffix (optional): ");
        Character suffix = scanner.nextLine().charAt(0);

        System.out.print("Separator (default ';'): ");
        Character separator = scanner.nextLine().charAt(0);
        
        scanner.close();


        System.out.println("===================================================");
        System.out.println("        Microcontroller Simulation Starting");
        System.out.println(" Targeting WeatherStation at " + weatherStationHost + ":" + weatherStationPort);
        System.out.println("===================================================");


        Microcontroller mc = new Microcontroller(id, location, weatherStationHost, weatherStationPort, prefix, suffix, separator);
        Thread mcThread = new Thread(mc::startSendingData, "Unicast-MC-" + 1);
        mcThread.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[" + new Date() + " Main] Shutdown hook triggered for Microcontrollers...");
            if (mc != null) {
                mc.stopSendingData();
            }

            if (mcThread != null && mcThread.isAlive()) {
                try {
                    System.out.println("[" + new Date() + " Main] Waiting for " + mcThread.getName() + " to finish...");
                    mcThread.join(1000);

                    if (mcThread.isAlive()) {
                        System.out.println("[" + new Date() + " Main] " + mcThread.getName() + " did not finish in time, interrupting.");
                        mcThread.interrupt(); 
                    }
                } catch (InterruptedException e) {
                    System.err.println("[" + new Date() + " Main] Interrupted while waiting for MC threads to join.");
                    Thread.currentThread().interrupt();
                }
            }

            System.out.println("[" + new Date() + " Main] Microcontroller shutdown process complete.");
        }));

        System.out.println("[" + new Date() + " Main] All microcontrollers launched. Press Ctrl+C to stop.");
    }


    private boolean connect() {
        if (socket != null && !socket.isClosed()) {
            return true;
        }

        try {
            System.out.println("[" + new Date() + " MC-" + id + "] Attempting to connect to WeatherStation at " +
                                weatherStationHost + ":" + weatherStationPort + "...");
            socket = new Socket(weatherStationHost, weatherStationPort);
            out = new PrintWriter(socket.getOutputStream(), true);

            System.out.println("[" + new Date() + " MC-" + id + "] Successfully connected to WeatherStation.");
            return true;

        } catch (UnknownHostException e) {
            System.err.println("[" + new Date() + " MC-" + id + "] Error: WeatherStation host not found '" + weatherStationHost + "'. " + e.getMessage());

        } catch (ConnectException e) {
            System.err.println("[" + new Date() + " MC-" + id + "] Error: Connection refused by WeatherStation at " +
                    weatherStationHost + ":" + weatherStationPort + ". Is WeatherStation running? " + e.getMessage());

        } catch (IOException e) {
            System.err.println("[" + new Date() + " MC-" + id + "] Error: I/O exception when connecting to WeatherStation: " + e.getMessage());
        }

        // If connection failed, clean up
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {}
            socket = null;
            out = null;
        }
        return false;
    }

    private void disconnect() {
        System.out.println("[" + new Date() + " MC-" + id + "] Disconnecting from WeatherStation...");
        
        try {
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) socket.close();

        } catch (IOException e) {
            System.err.println("[" + new Date() + " MC-" + id + "] Error while disconnecting: " + e.getMessage());

        } finally {
            out = null;
            socket = null;
        }
        System.out.println("[" + new Date() + " MC-" + id + "] Disconnected.");
    }


    public void startSendingData() {
        System.out.println("[" + new Date() + " MC-" + id + "] Starting data transmission to WeatherStation " +
                weatherStationHost + ":" + weatherStationPort);

        while (isRunning && !Thread.currentThread().isInterrupted()) {
            if (socket == null || socket.isClosed() || out == null || out.checkError()) {
                disconnect();

                System.out.println("[" + new Date() + " MC-" + id + "] Connection lost or not established. Attempting to reconnect...");

                if (!connect()) {
                    System.err.println("[" + new Date() + " MC-" + id + "] Failed to connect. Retrying in " + RECONNECT_DELAY_MS / 1000 + " seconds...");

                    try {
                        TimeUnit.MILLISECONDS.sleep(RECONNECT_DELAY_MS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        System.out.println("[" + new Date() + " MC-" + id + "] Reconnect sleep interrupted.");
                    }
                }
            }

            float pressure = 950.0f + random.nextFloat() * (1050.0f - 950.0f);
            float radiation = random.nextFloat() * 1400.0f;
            float temperature = -20.0f + random.nextFloat() * (50.0f - (-20.0f));
            float humidity = random.nextFloat() * 100.0f;

            String dataString = String.format(Locale.US, "%c%d%c%s%c%.2f%c%.2f%c%.2f%c%.2f%c",
                    this.prefix, this.id, this.separator, location, this.separator, pressure, this.separator, radiation, this.separator, temperature, this.separator, humidity, this.suffix);

            try {
                if (out != null && !out.checkError()) {
                    out.println(dataString);
                    // out.flush();
                    System.out.println("[" + new Date() + " MC-" + id + "] Sent data: \"" + dataString + "\"");
                } else {
                    System.err.println("[" + new Date() + " MC-" + id + "] Output stream not available or in error state. Will attempt to reconnect.");
                    disconnect();
                    continue;
                }

            } catch (Exception e) {
                System.err.println("[" + new Date() + " MC-" + id + "] Error sending data: " + e.getMessage());
                disconnect();
            }

            int sleepTimeMs = 2000 + random.nextInt(3001); // 2 to 5 seconds

            try {
                TimeUnit.MILLISECONDS.sleep(sleepTimeMs);
            } catch (InterruptedException e) {
                System.out.println("[" + new Date() + " MC-" + id + "] Send loop interrupted.");
                isRunning = false;
                Thread.currentThread().interrupt();
            }
        }

        disconnect();
        System.out.println("[" + new Date() + " MC-" + id + "] Stopped sending data.");
    }

    public void stopSendingData() {
        this.isRunning = false;
        disconnect();
    }
}