package com.wind;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.IOException;

import java.net.Socket;
import java.util.Scanner;

import com.wind.View;
import com.wind.message.Message;
import com.wind.Controller;

public class Client {
    public static Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) throws Exception {
        // Connect to location server and get the proxy adress and port
        String proxyAdress = "";
        int proxyPort = -1;

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

        System.out.print("Enter the location server adress: ");
        String locationAdress = scanner.nextLine();

        System.out.print("Enter the location server port: ");
        int locationServerPort = scanner.nextInt();

        String newProxy = getNewProxy(locationAdress, locationServerPort);
        proxyAdress = newProxy.split(":")[0];
        proxyPort = Integer.parseInt(newProxy.split(":")[1]);

        System.out.println("\nConnecting to proxy server at " + proxyAdress + ":" + proxyPort + "...");

        // Initiate the system for the user
        while (proxyPort != -1 && proxyAdress != "") {
            try(Socket proxySocket = new Socket(proxyAdress, proxyPort);
            ObjectOutputStream proxyOut = new ObjectOutputStream(proxySocket.getOutputStream());
            ObjectInputStream proxyIn = new ObjectInputStream(proxySocket.getInputStream())) {
     
                // Set the controller
                Controller.setProxyServer(proxyOut, proxyIn);

                // Start the user interface
                View.clearScreen();
                View.showMenu();

                // Close the loop
                proxyPort = -1;
                proxyAdress = "";

            } catch (IOException e) {
                System.out.println("\nProxy server went down. Changing server...");
                newProxy = getNewProxy(locationAdress, locationServerPort);

                String newProxyAdress = newProxy.split(":")[0];
                int newProxyPort = Integer.parseInt(newProxy.split(":")[1]);
                
                // Se o proxy adress estiver vazio, ou se o novo proxy for igual ao antigo, encerra o programa
                if (newProxyAdress.equals("") || (newProxyAdress.equals(proxyAdress) && newProxyPort == proxyPort)) {
                    System.out.println(Color.RED + "No proxy available. Exiting..." + Color.RESET);
                    break;
                } else {
                    proxyAdress = newProxyAdress;
                    proxyPort = newProxyPort;
                }
            }
        }

        scanner.close();
    }

    private static String getNewProxy(String locationAdress, int locationServerPort) {
        String newProxyAdress = "";
        int newProxyPort = -1;

        try (Socket locationSocket = new Socket(locationAdress, locationServerPort);
             DataInputStream locationIn = new DataInputStream(locationSocket.getInputStream());
             DataOutputStream locationOut = new DataOutputStream(locationSocket.getOutputStream())) {

            locationOut.writeUTF("eW91IHNoYWxsIG5vdCBwYXNz");  // Send a key as request to the location server
            newProxyAdress = locationIn.readUTF();  // Read the proxy adress
            newProxyPort = locationIn.readInt();  // Read the proxy port

        } catch (IOException e) {
            System.out.println(Color.RED + "\nError. Location server went down, or there's no proxy available." + Color.RESET);
        }

        return newProxyAdress + ":" + newProxyPort;
    }
}