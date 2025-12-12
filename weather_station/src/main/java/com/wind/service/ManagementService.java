package com.wind.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.wind.entities.MicrocontrollerEntity;
import com.wind.model.DAO.MicrocontrollerDAO;
import com.wind.security.RSA;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.List;
import java.util.Map;

public class ManagementService {

    private final int port;
    private final MicrocontrollerDAO microcontrollerDAO;
    private final ObjectMapper objectMapper;
    private HttpServer server;
    
    private final PublicKey publicKey;
    private final PrivateKey privateKey;
    private final Map<Integer, byte[]> microcontrollerKeys;

    public ManagementService(int port, MicrocontrollerDAO microcontrollerDAO, PublicKey publicKey, PrivateKey privateKey, Map<Integer, byte[]> microcontrollerKeys) {
        this.port = port;
        this.microcontrollerDAO = microcontrollerDAO;
        this.objectMapper = new ObjectMapper();
        this.publicKey = publicKey;
        this.privateKey = privateKey;
        this.microcontrollerKeys = microcontrollerKeys;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/weather/microcontrollers", new MicrocontrollerHandler());
        server.createContext("/weather/security/public-key", new PublicKeyHandler());
        server.createContext("/weather/security/handshake", new HandshakeHandler());
        server.setExecutor(null); // creates a default executor
        server.start();
        System.out.println("[Management Service] Started on port " + port);
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            System.out.println("[Management Service] Stopped.");
        }
    }

    private class PublicKeyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equals(exchange.getRequestMethod())) {
                String response = RSA.publicKeyToBase64(publicKey);
                sendResponse(exchange, 200, response);
            } else {
                sendResponse(exchange, 405, "Method not allowed");
            }
        }
    }

    private class HandshakeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                try {
                    // Parse Query Params for mcId
                    String query = exchange.getRequestURI().getQuery();
                    String mcIdParam = null;
                    if (query != null) {
                        for (String param : query.split("&")) {
                            String[] pair = param.split("=");
                            if (pair.length == 2 && "mcId".equals(pair[0])) {
                                mcIdParam = pair[1];
                                break;
                            }
                        }
                    }

                    if (mcIdParam == null) {
                        sendResponse(exchange, 400, "Missing mcId parameter");
                        return;
                    }

                    int mcId = Integer.parseInt(mcIdParam);
                    
                    // Read Body
                    String encryptedAesKey = new String(exchange.getRequestBody().readAllBytes());
                    
                    // Decrypt AES Key
                    byte[] aesKey = RSA.decrypt(encryptedAesKey, privateKey);
                    
                    if (microcontrollerKeys.containsKey(mcId)) {
                        System.out.println("[HANDSHAKE REJECTED] Microcontroller " + mcId + " already registered.");
                        sendResponse(exchange, 409, "Microcontroller ID already registered");
                        return;
                    }

                    microcontrollerKeys.put(mcId, aesKey);
                    System.out.println("[HANDSHAKE] Successful handshake for Microcontroller " + mcId);
                    sendResponse(exchange, 200, "Handshake successful");

                } catch (Exception e) {
                    e.printStackTrace();
                    sendResponse(exchange, 500, "Handshake failed: " + e.getMessage());
                }
            } else {
                sendResponse(exchange, 405, "Method not allowed");
            }
        }
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, response.length());
        OutputStream os = exchange.getResponseBody();
        os.write(response.getBytes());
        os.close();
    }

    private class MicrocontrollerHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            String[] pathParts = path.split("/");

            try {
                if (method.equals("GET")) {
                    if (pathParts.length == 3) { // /weather/microcontrollers
                        handleList(exchange);
                    } else if (pathParts.length == 4) { // /weather/microcontrollers/{id}
                        handleGet(exchange, Integer.parseInt(pathParts[3]));
                    } else {
                        sendResponse(exchange, 400, "Invalid path");
                    }
                } else if (method.equals("POST")) {
                    handleCreate(exchange);
                } else if (method.equals("PUT")) {
                    handleUpdate(exchange);
                } else if (method.equals("DELETE")) {
                    if (pathParts.length == 4) {
                        handleDelete(exchange, Integer.parseInt(pathParts[3]));
                    } else {
                        sendResponse(exchange, 400, "Invalid path for DELETE");
                    }
                } else {
                    sendResponse(exchange, 405, "Method not allowed");
                }
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "Internal Server Error: " + e.getMessage());
            }
        }

        private void handleList(HttpExchange exchange) throws IOException {
            List<MicrocontrollerEntity> list = microcontrollerDAO.getAllMicrocontrollers();
            String response = objectMapper.writeValueAsString(list);
            sendResponse(exchange, 200, response);
        }

        private void handleGet(HttpExchange exchange, int id) throws IOException {
            MicrocontrollerEntity mc = microcontrollerDAO.getMicrocontroller(id);
            if (mc != null) {
                String response = objectMapper.writeValueAsString(mc);
                sendResponse(exchange, 200, response);
            } else {
                sendResponse(exchange, 404, "Microcontroller not found");
            }
        }

        private void handleCreate(HttpExchange exchange) throws IOException {
            MicrocontrollerEntity mc = objectMapper.readValue(exchange.getRequestBody(), MicrocontrollerEntity.class);
            microcontrollerDAO.addMicrocontroller(mc);
            sendResponse(exchange, 201, "Created");
        }

        private void handleUpdate(HttpExchange exchange) throws IOException {
            MicrocontrollerEntity mc = objectMapper.readValue(exchange.getRequestBody(), MicrocontrollerEntity.class);
            microcontrollerDAO.updateMicrocontroller(mc);
            sendResponse(exchange, 200, "Updated");
        }

        private void handleDelete(HttpExchange exchange, int id) throws IOException {
            microcontrollerDAO.deleteMicrocontroller(id);
            sendResponse(exchange, 200, "Deleted");
        }
    }
}
