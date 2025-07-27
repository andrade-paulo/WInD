package com.wind;

import com.wind.controller.RegistryController;
import com.wind.service.HealthCheckService;
import com.wind.service.RegistryService;
import io.javalin.Javalin;


public class ServiceDiscoveryApplication {
    public static void main(String[] args) {
        // 1. Inicializa os serviços de lógica
        RegistryService registryService = new RegistryService();
        HealthCheckService healthCheckService = new HealthCheckService(registryService);
        
        // 2. Inicializa o controller com a lógica
        RegistryController registryController = new RegistryController(registryService);

        // 3. Configura e inicia o servidor web Javalin
        Javalin app = Javalin.create().start(7000); // O Service Discovery roda na porta 7000
        
        System.out.println("Service Discovery iniciado na porta 7000.");
        
        // 4. Registra as rotas da API
        registryController.createRoutes(app);
        
        // 5. Inicia o serviço de health check em background
        healthCheckService.start();

        // Adiciona um gancho de desligamento para parar os serviços graciosamente
        Runtime.getRuntime().addShutdownHook(new Thread(healthCheckService::stop));
    }
}