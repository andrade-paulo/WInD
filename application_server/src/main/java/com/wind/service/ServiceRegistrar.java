package com.wind.service;


import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class ServiceRegistrar {
    private static final String DISCOVERY_SERVICE_URL = "http://servicediscovery:7000";
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void register(ServiceInstancePayload payload) {
        try {
            String requestBody = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(DISCOVERY_SERVICE_URL + "/register"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 201) {
                System.out.println("Aplicação registrada com sucesso no Service Discovery: " + payload.getInstanceId());
            } else {
                System.err.println("Falha ao registrar no Service Discovery. Status: " + response.statusCode() + " | Resposta: " + response.body());
            }
        } catch (Exception e) {
            System.err.println("Erro crítico ao tentar registrar serviço: " + e.getMessage());
            // To-Do: Implementar lógica de retry ou fallback
        }
    }
}
