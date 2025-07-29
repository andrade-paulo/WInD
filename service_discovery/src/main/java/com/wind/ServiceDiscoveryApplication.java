package com.wind;

import com.wind.controller.RegistryController;
import com.wind.service.HealthCheckService;
import com.wind.service.RegistryService;
import io.javalin.Javalin;


public class ServiceDiscoveryApplication {
    public static void main(String[] args) {
        RegistryService registryService = new RegistryService();
        HealthCheckService healthCheckService = new HealthCheckService(registryService);
        
        RegistryController registryController = new RegistryController(registryService);

        Javalin app = Javalin.create().start(7000);
        
        System.out.println("Service Discovery iniciado na porta 7000.");
        
        registryController.createRoutes(app);
        
        // Serviço de health check em background
        healthCheckService.start();

        // Adiciona um gancho de desligamento para parar os serviços
        Runtime.getRuntime().addShutdownHook(new Thread(healthCheckService::stop));
    }
}