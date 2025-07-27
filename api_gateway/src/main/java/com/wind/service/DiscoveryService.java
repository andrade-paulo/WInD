package com.wind.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class DiscoveryService {
    private static final String SERVICE_DISCOVERY_URL = "http://servicediscovery:7000"; // Endereço do nosso outro serviço
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    
    // Mapa para o balanceamento de carga Round Robin
    private final Map<String, AtomicInteger> roundRobinCounters = new ConcurrentHashMap<>();

    public DiscoveryService() {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    public Optional<String> discoverServiceAddress(String serviceName) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(SERVICE_DISCOVERY_URL + "/services/" + serviceName))
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                System.err.println("Erro ao buscar serviço '" + serviceName + "': " + response.statusCode());
                return Optional.empty();
            }

            List<String> addresses = objectMapper.readValue(response.body(), new TypeReference<>() {});

            if (addresses.isEmpty()) {
                System.err.println("Nenhuma instância saudável encontrada para o serviço: " + serviceName);
                return Optional.empty();
            }
            
            // Lógica de balanceamento de carga Round Robin
            return Optional.of(getNextAddress(serviceName, addresses));

        } catch (Exception e) {
            System.err.println("Exceção ao contatar o Service Discovery: " + e.getMessage());
            return Optional.empty();
        }
    }
    
    private String getNextAddress(String serviceName, List<String> addresses) {
        // Obtém o contador para este serviço, ou cria um novo se não existir.
        AtomicInteger counter = roundRobinCounters.computeIfAbsent(serviceName, k -> new AtomicInteger(0));
        // Obtém o próximo índice de forma atômica e circular.
        int index = counter.getAndIncrement() % addresses.size();
        return addresses.get(index);
    }
}