package com.wind.service;


import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ServiceRegistrar {
    private static final String DISCOVERY_SERVICE_URL = "http://service-discovery:7000";
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ServiceInstancePayload payload;

    public boolean register(ServiceInstancePayload payload) {
        this.payload = payload;
        int maxRetries = 5;
        long delayMs = 3000; // 3 segundos de espera entre tentativas

        for (int i = 0; i < maxRetries; i++) {
            try {
                System.out.println("Tentando registrar serviço (tentativa " + (i + 1) + ")...");
                String requestBody = objectMapper.writeValueAsString(payload);

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(DISCOVERY_SERVICE_URL + "/register"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(5)) // Adiciona um timeout
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 201) {
                    System.out.println("Aplicação registrada com sucesso no Service Discovery: " + payload.getInstanceId());
                    return true; // Sucesso! Sai do método.
                } else {
                    System.err.println("Falha ao registrar (tentativa " + (i + 1) + "). Status: " + response.statusCode());
                }
            } catch (Exception e) {
                System.err.println("Erro de conexão ao registrar (tentativa " + (i + 1) + "): " + e.getClass().getSimpleName());
            }

            // Se chegou aqui, a tentativa falhou. Espera antes de tentar novamente.
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        System.err.println("ERRO CRÍTICO: Não foi possível registrar o serviço após " + maxRetries + " tentativas. A aplicação pode não funcionar corretamente.");
        return false;
    }

    public void startHeartbeat() {
        // O intervalo de 12s é seguro, pois o timeout do health check é de 30s.
        scheduler.scheduleAtFixedRate(this::sendHeartbeat, 10, 12, TimeUnit.SECONDS);
        System.out.println("Agendador de heartbeat iniciado.");
    }

    private void sendHeartbeat() {
        try {
            String requestBody = objectMapper.writeValueAsString(this.payload);
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(DISCOVERY_SERVICE_URL + "/heartbeat"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(5))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() != 200) {
                        System.err.println("Falha no heartbeat. Status: " + response.statusCode() + ". O serviço pode precisar se registrar novamente.");
                    }
                })
                .exceptionally(ex -> {
                    System.err.println("Erro de conexão no heartbeat: " + ex.getMessage());
                    return null;
                });
        } catch (Exception e) {
            System.err.println("Erro ao preparar a requisição de heartbeat: " + e.getMessage());
        }
    }

    public void shutdown() {
        System.out.println("Encerrando o agendador de heartbeat...");
        scheduler.shutdown();
    }
}