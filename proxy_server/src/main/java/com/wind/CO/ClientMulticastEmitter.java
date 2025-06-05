package com.wind.CO;

import com.wind.entities.WeatherData;
import com.wind.message.Message;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class ClientMulticastEmitter {
    private final String multicastAddress;
    private final int multicastPort;
    private DatagramSocket socket;
    private InetAddress group;
    private static final int MAX_UDP_PACKET_SIZE = 65507; // Max UDP payload size


    public ClientMulticastEmitter(String address, int port) throws IOException {
        this.multicastAddress = address;
        this.multicastPort = port;
        this.socket = new DatagramSocket();
        this.group = InetAddress.getByName(multicastAddress);
        System.out.println("[Proxy] Multicast Emitter ready for " + multicastAddress + ":" + multicastPort);
    }

    public void multicastData(WeatherData data) {
        if (data == null) {
            System.err.println("[Proxy] Cannot multicast null WeatherData.");
            return;
        }
        try {
            // Create a Message object for client update
            Message messageToClients = new Message(data, "CLIENTWEATHERUPDATE");

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(messageToClients);
            oos.flush();
            byte[] buffer = baos.toByteArray();
            oos.close();
            baos.close();

            if (buffer.length > MAX_UDP_PACKET_SIZE) {
                System.err.println("[Proxy] Serialized Message size (" + buffer.length + " bytes) exceeds UDP packet limit for WeatherData ID: " + data.getId());
                return;
            }

            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, multicastPort);
            socket.send(packet);
            System.out.println("[Proxy -> Clients] Multicasted Message with WeatherData from station: " + data.getId() + " (Size: " + buffer.length + " bytes)");

        } catch (IOException e) {
            System.err.println("[Proxy] Error multicasting Message: " + e.getMessage());
        }
    }

    public void close() {
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        System.out.println("[Proxy] Multicast Emitter closed.");
    }
}