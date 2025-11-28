package com.wind.service;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ServiceDiscoveryService {

    private final String serviceDiscoveryUrl;
    private final String serviceName;
    private final String instanceId;
    private final String serviceAddress;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private ScheduledExecutorService heartbeatScheduler;

    public ServiceDiscoveryService(String serviceDiscoveryUrl, String serviceName, String instanceId, String serviceAddress) {
        this.serviceDiscoveryUrl = serviceDiscoveryUrl;
        this.serviceName = serviceName;
        this.instanceId = instanceId;
        this.serviceAddress = serviceAddress;
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    public void registerService() {
        try {
            Map<String, Object> serviceInfo = new HashMap<>();
            serviceInfo.put("serviceName", serviceName);
            serviceInfo.put("instanceId", instanceId);
            serviceInfo.put("address", serviceAddress);
            serviceInfo.put("healthCheckUrl", null);

            String jsonBody = objectMapper.writeValueAsString(serviceInfo);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serviceDiscoveryUrl + "/register"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 201) {
                System.out.println("[Service Discovery] Registered successfully as " + instanceId);
            } else {
                System.err.println("[Service Discovery] Registration failed: " + response.statusCode() + " - " + response.body());
            }
        } catch (Exception e) {
            System.err.println("[Service Discovery] Error registering service: " + e.getMessage());
        }
    }

    public void startHeartbeat() {
        heartbeatScheduler = Executors.newSingleThreadScheduledExecutor();
        heartbeatScheduler.scheduleAtFixedRate(this::sendHeartbeat, 10, 30, TimeUnit.SECONDS);
    }

    private void sendHeartbeat() {
        try {
            Map<String, Object> heartbeatInfo = new HashMap<>();
            heartbeatInfo.put("serviceName", serviceName);
            heartbeatInfo.put("instanceId", instanceId);

            String jsonBody = objectMapper.writeValueAsString(heartbeatInfo);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serviceDiscoveryUrl + "/heartbeat"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 404) {
                System.err.println("[Service Discovery] Instance not found during heartbeat. Re-registering...");
                registerService();
            } else if (response.statusCode() != 200) {
                System.err.println("[Service Discovery] Heartbeat failed: " + response.statusCode());
            }
        } catch (Exception e) {
            System.err.println("[Service Discovery] Error sending heartbeat: " + e.getMessage());
        }
    }

    public void deregisterService() {
        try {
            Map<String, Object> serviceInfo = new HashMap<>();
            serviceInfo.put("serviceName", serviceName);
            serviceInfo.put("instanceId", instanceId);

            String jsonBody = objectMapper.writeValueAsString(serviceInfo);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serviceDiscoveryUrl + "/deregister"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("[Service Discovery] Deregistered successfully.");
        } catch (Exception e) {
            System.err.println("[Service Discovery] Error deregistering service: " + e.getMessage());
        }
    }

    public void stop() {
        if (heartbeatScheduler != null) {
            heartbeatScheduler.shutdown();
        }
    }
}
