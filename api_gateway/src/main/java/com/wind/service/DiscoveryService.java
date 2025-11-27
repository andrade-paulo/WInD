package com.wind.service;


import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class DiscoveryService {
    private static final String SERVICE_DISCOVERY_URL = "http://localhost:7000";
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Map<String, AtomicInteger> roundRobinCounters = new ConcurrentHashMap<>();

    public DiscoveryService() {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    public Optional<String> discoverServiceAddress(String serviceName) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(SERVICE_DISCOVERY_URL + "/services/" + serviceName))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                 List<String> addresses = objectMapper.readValue(response.body(), new TypeReference<>() {});
                 if (addresses.isEmpty()) {
                    System.err.println("Nenhuma instância saudável encontrada para o serviço: " + serviceName);
                    return Optional.empty();
                 } else {
                    return Optional.of(getNextAddress(serviceName, addresses));
                 }
            } else {
                System.err.println("Erro ao buscar serviço '" + serviceName + "': Status " + response.statusCode());
                return Optional.empty();
            }
        } catch (Exception e) {
            // Imprime o nome da exceção para evitar a mensagem "null"
            System.err.println("Exceção ao contatar o Service Discovery: " + e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private String getNextAddress(String serviceName, List<String> addresses) {
        AtomicInteger counter = roundRobinCounters.computeIfAbsent(serviceName, k -> new AtomicInteger(0));
        int index = counter.getAndIncrement() % addresses.size();
        return addresses.get(index);
    }
}