package com.wind.service;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

public class UdpService {

    private final int ingressPort;
    private final String egressHost;
    private final int egressPort;

    private DatagramSocket ingressSocket;
    private DatagramSocket egressSocket;
    private volatile boolean isRunning = true;

    public UdpService(int ingressPort, String egressHost, int egressPort) {
        this.ingressPort = ingressPort;
        this.egressHost = egressHost;
        this.egressPort = egressPort;
    }

    public void setupEgress() throws SocketException {
        this.egressSocket = new DatagramSocket();
        System.out.println("[Egress UDP] Socket created.");
    }

    public void startIngressListener(Consumer<String> messageProcessor) {
        new Thread(() -> {
            try {
                ingressSocket = new DatagramSocket(ingressPort);
                byte[] buffer = new byte[1024];

                while (isRunning) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    try {
                        ingressSocket.receive(packet);
                        String rawPayload = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);

                        System.out.println("\n-> [UDP RCV] From: " + packet.getAddress() + ":" + packet.getPort());
                        System.out.println("   Payload: " + rawPayload);

                        messageProcessor.accept(rawPayload);

                    } catch (IOException e) {
                        if (isRunning) {
                            System.err.println("[Ingress UDP] Error receiving packet: " + e.getMessage());
                        }
                    }
                }
            } catch (SocketException e) {
                System.err.println("[Ingress UDP] Could not bind to port " + ingressPort + ": " + e.getMessage());
            }
        }).start();
    }

    public void send(String payload) {
        try {
            if (egressSocket != null && !egressSocket.isClosed()) {
                byte[] data = payload.getBytes(StandardCharsets.UTF_8);
                InetAddress address = InetAddress.getByName(egressHost);
                DatagramPacket packet = new DatagramPacket(data, data.length, address, egressPort);

                egressSocket.send(packet);
                System.out.println("<- [EGRESS UDP] Sent to " + egressHost + ":" + egressPort);
            } else {
                System.err.println("[EGRESS UDP] Cannot send, socket not available.");
            }
        } catch (IOException e) {
            System.err.println("[EGRESS UDP] Error sending packet: " + e.getMessage());
        }
    }

    public void stop() {
        isRunning = false;
        if (ingressSocket != null && !ingressSocket.isClosed()) {
            ingressSocket.close();
        }
        if (egressSocket != null && !egressSocket.isClosed()) {
            egressSocket.close();
        }
    }
}
