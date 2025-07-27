package com.wind.service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


public class HealthCheckService {
    private final RegistryService registryService;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final long timeoutSeconds = 30; // Tempo máximo sem resposta antes de remover

    public HealthCheckService(RegistryService registryService) {
        this.registryService = registryService;
    }

    public void start() {
        // Agenda a tarefa para rodar a cada 10 segundos, após um delay inicial de 15 segundos.
        scheduler.scheduleAtFixedRate(this::checkServices, 15, 10, TimeUnit.SECONDS);
        System.out.println("Health Check Service iniciado.");
    }

    public void stop() {
        scheduler.shutdown();
    }

    private void checkServices() {
        System.out.println("Executando health check...");
        Instant now = Instant.now();
        registryService.getRegistry().forEach((serviceName, instances) -> {
            instances.values().forEach(instance -> {
                long secondsSinceHeartbeat = Duration.between(instance.getLastHeartbeat(), now).getSeconds();
                if (secondsSinceHeartbeat > timeoutSeconds) {
                    System.out.println("Instância inativa detectada: " + instance.getInstanceId() + ". Removendo.");
                    registryService.deregister(serviceName, instance.getInstanceId());
                }
            });
        });
    }
}