package com.wind;

import com.wind.entities.WeatherData;
import com.wind.message.Message;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.util.Date;

public class Client {
    private static String LOCATION_SERVER_HOST = "localhost";
    private static int LOCATION_SERVER_PORT = 4000;
    private static final String CLIENT_AUTH_KEY_TO_LOCATION_SERVER = "bWF5IHRoZSBmb3JjZSBiZSB3aXRoIHlvdQ==";

    private static final int BUFFER_SIZE = 65507;

    private static class MulticastInfo {
        final String address;
        final int port;

        MulticastInfo(String address, int port) {
            this.address = address;
            this.port = port;
        }

        boolean isValid() {
            return address != null && !address.isEmpty() && port > 0 && port < 65536;
        }

        @Override
        public String toString() {
            return "Multicast Group: " + address + ":" + port;
        }
    }


    public static void main(String[] args) {
        if (args.length >= 1) {
            LOCATION_SERVER_HOST = args[0];
        }
        if (args.length >= 2) {
            try {
                LOCATION_SERVER_PORT = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number for LocationServer: '" + args[1] +
                        "'. Using default: " + LOCATION_SERVER_PORT);
            }
        }
        if (args.length > 2) {
             System.out.println("Usage: java com.wind.Client [<location_server_host> <location_server_port>]");
        }


        System.out.println("\r\n" + //
                            "===========================================================\r\n" + //
                            "                  _       ______      ____                 \r\n" + //
                            "                 | |     / /  _/___  / __ \\               \r\n" + //
                            "                 | | /| / // // __ \\/ / / /               \r\n" + //
                            "                 | |/ |/ // // / / / /_/ /                 \r\n" + //
                            "                 |__/|__/___/_/ /_/_____/                  \r\n" + //
                            "                                                           \r\n" + //
                            "              Weather Information Distributor              \r\n" + //
                            "===========================================================\r\n");


        MulticastInfo multicastInfo = getMulticastDetailsFromLocationServer();

        if (multicastInfo == null || !multicastInfo.isValid()) {
            System.err.println("[" + new Date() + " Client] Failed to obtain valid multicast details. Exiting.");
            return;
        }

        MulticastSocket multicastSocket = null;
        System.out.println("[" + new Date() + " Client] Attempting to join multicast group " +
                multicastInfo.address + " on port " + multicastInfo.port + "...");

        try {
            InetAddress group = InetAddress.getByName(multicastInfo.address);
            multicastSocket = new MulticastSocket(multicastInfo.port);

            SocketAddress socketAddress = new java.net.InetSocketAddress(group, multicastInfo.port);
            NetworkInterface networkInterface = NetworkInterface.getByInetAddress(group);

             NetworkInterface netIf = null;

             try {
                 netIf = NetworkInterface.getByInetAddress(InetAddress.getLocalHost()); // A guess
                 if (netIf == null || !netIf.supportsMulticast() || !netIf.isUp() || netIf.isLoopback()) {
                     System.out.println("[" + new Date() + " Client] Could not find a suitable default network interface automatically, trying generic join.");
                     netIf = null; // Fallback to OS default
                 } else {
                     System.out.println("[" + new Date() + " Client] Attempting to use network interface: " + netIf.getDisplayName());
                 }
             } catch (Exception e) {
                 System.out.println("[" + new Date() + " Client] Error detecting network interface: " + e.getMessage() + ". Using OS default for join.");
             }

            if (netIf != null) {
                 multicastSocket.joinGroup(new java.net.InetSocketAddress(group, multicastInfo.port), netIf);
            } else {
                 multicastSocket.joinGroup(socketAddress, networkInterface); // Simpler join, OS picks interface
            }


            System.out.println("[" + new Date() + " Client] Successfully joined multicast group. Waiting for weather data...");
            System.out.println("-----------------------------------------------------------");

            byte[] buffer = new byte[BUFFER_SIZE];

            while (!Thread.currentThread().isInterrupted()) { // Loop until interrupted
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                try {
                    multicastSocket.receive(packet); // Blocks until a packet is received

                    ByteArrayInputStream bais = new ByteArrayInputStream(packet.getData(), 0, packet.getLength());
                    ObjectInputStream ois = new ObjectInputStream(bais);
                    Message receivedMessage = (Message) ois.readObject();
                    ois.close();
                    bais.close();

                    String instruction = receivedMessage.getInstrucao();

                    if ("CLIENTWEATHERUPDATE".equals(instruction)) {
                        WeatherData data = receivedMessage.getWeatherData(); 
                        System.out.println("\n[" + new Date() + " Client]");
                        System.out.println(data.toString());
                        System.out.println(" ===================================");
                    } else {
                        System.out.println("\n[" + new Date() + " Client] Received a message with unrecognized instruction: \"" + instruction + "\"");
                    }

                } catch (SocketException se) {
                    System.err.println("[" + new Date() + " Client] SocketException (e.g. socket closed): " + se.getMessage() + ". Exiting loop.");
                    break;
                } catch (IOException e) {
                    System.err.println("[" + new Date() + " Client] Error receiving or processing packet: " + e.getMessage());
                    // e.printStackTrace();
                } catch (ClassNotFoundException e) {
                    System.err.println("[" + new Date() + " Client] Error: Message class or dependency not found. " + e.getMessage());
                    // e.printStackTrace();
                } catch (ParseException e) {
                    System.err.println("[" + new Date() + " Client] Error parsing WeatherData from message: " + e.getMessage());
                    // e.printStackTrace();
                } catch (Exception e) { // Catch-all for other unexpected errors
                    System.err.println("[" + new Date() + " Client] An unexpected runtime error occurred: " + e.getMessage());
                    e.printStackTrace();
                }
            }

        } catch (IOException e) {
            System.err.println("[" + new Date() + " Client] Could not join multicast group or critical network error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (multicastSocket != null && !multicastSocket.isClosed()) {
                try {
                    InetAddress groupAddress = InetAddress.getByName(multicastInfo.address);
                    InetAddress group = InetAddress.getByName(multicastInfo.address);
                    SocketAddress socketAddress = new java.net.InetSocketAddress(group, multicastInfo.port);
                    NetworkInterface networkInterface = NetworkInterface.getByInetAddress(group);

                    NetworkInterface netIf = null;
                    try {
                        netIf = NetworkInterface.getByInetAddress(InetAddress.getLocalHost());
                         if (netIf == null || !netIf.supportsMulticast() || !netIf.isUp() || netIf.isLoopback()) netIf = null;
                    } catch (Exception e) { /* ignore */ }

                    if (netIf != null) {
                        multicastSocket.leaveGroup(new java.net.InetSocketAddress(groupAddress, multicastInfo.port), netIf);
                    } else {
                        multicastSocket.leaveGroup(socketAddress, networkInterface);
                    }
                    System.out.println("\n[" + new Date() + " Client] Left multicast group: " + multicastInfo.address);

                } catch (IOException e) {
                    System.err.println("[" + new Date() + " Client] Error leaving multicast group: " + e.getMessage());
                }

                multicastSocket.close();
                System.out.println("[" + new Date() + " Client] Multicast socket closed.");
            }
             System.out.println("[" + new Date() + " Client] Client shutdown complete.");
        }
    }


    private static MulticastInfo getMulticastDetailsFromLocationServer() {
        System.out.println("[" + new Date() + " Client] Attempting to contact LocationServer at " +
                LOCATION_SERVER_HOST + ":" + LOCATION_SERVER_PORT + " for multicast details...");

        try (Socket socket = new Socket(LOCATION_SERVER_HOST, LOCATION_SERVER_PORT);
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
             DataInputStream dis = new DataInputStream(socket.getInputStream())) {

            socket.setSoTimeout(5000);

            // Send the client authentication key
            dos.writeUTF(CLIENT_AUTH_KEY_TO_LOCATION_SERVER);
            dos.flush();
            System.out.println("[" + new Date() + " Client] Sent authentication key to LocationServer.");

            // Read multicast address and port from LocationServer
            String multicastAddress = dis.readUTF();
            int multicastPort = dis.readInt();

            MulticastInfo info = new MulticastInfo(multicastAddress, multicastPort);

            if (info.isValid()) {
                System.out.println("[" + new Date() + " Client] Successfully received multicast details from LocationServer: " + info);
                return info;
            } else {
                System.err.println("[" + new Date() + " Client] LocationServer returned invalid multicast details (Address: '" +
                        multicastAddress + "', Port: " + multicastPort + "). ProxyServer might not be registered or LocationServer has issues.");
                return null;
            }

        } catch (UnknownHostException e) {
            System.err.println("[" + new Date() + " Client] Error: LocationServer host not found '" + LOCATION_SERVER_HOST + "'. " + e.getMessage());
        } catch (SocketTimeoutException e) {
            System.err.println("[" + new Date() + " Client] Error: Timeout connecting to or reading from LocationServer. " + e.getMessage());
        } catch (IOException e) {
            System.err.println("[" + new Date() + " Client] Error: I/O exception when communicating with LocationServer at " +
                    LOCATION_SERVER_HOST + ":" + LOCATION_SERVER_PORT + ". Is it running? " + e.getMessage());
        }
        return null;
    }
}
