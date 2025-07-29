package com.wind.controller;

import com.wind.model.ServiceInstance;
import com.wind.service.RegistryService;
import io.javalin.Javalin;
import io.javalin.http.Context;
import java.util.List;
import java.util.stream.Collectors;


public class RegistryController {
    private final RegistryService registryService;

    public RegistryController(RegistryService registryService) {
        this.registryService = registryService;
    }

    public void createRoutes(Javalin app) {
        app.get("/health", ctx -> ctx.status(200).result("OK"));
        app.post("/register", this::register);
        app.post("/heartbeat", this::heartbeat);
        app.post("/deregister", this::deregister);
        app.get("/services/{serviceName}", this::getServiceInstances);
        app.get("/registry/view", this::viewRegistry); // Endpoint para visualização
    }

    private void register(Context ctx) {
        // Converte o JSON do corpo da requisição para um objeto ServiceInstance
        ServiceInstance instance = ctx.bodyAsClass(ServiceInstance.class);
        registryService.register(instance);
        ctx.status(201).result("Serviço registrado com sucesso.");
    }
    
    private void deregister(Context ctx) {
        ServiceInstance instance = ctx.bodyAsClass(ServiceInstance.class);
        registryService.deregister(instance.getServiceName(), instance.getInstanceId());
        ctx.status(200).result("Serviço desregistrado.");
    }

    private void heartbeat(Context ctx) {
        ServiceInstance instanceInfo = ctx.bodyAsClass(ServiceInstance.class);
        boolean updated = registryService.receiveHeartbeat(instanceInfo.getServiceName(), instanceInfo.getInstanceId());
        if (updated) {
            ctx.status(200).result("Heartbeat recebido.");
        } else {
            // Retornar 404 para que o cliente saiba que precisa se registrar novamente.
            ctx.status(404).result("Instância não encontrada. O serviço deve se registrar novamente.");
        }
    }

    private void getServiceInstances(Context ctx) {
        String serviceName = ctx.pathParam("serviceName");
        List<ServiceInstance> instances = registryService.getHealthyInstances(serviceName);
        
        // Retorna apenas uma lista de endereços, como combinado no design
        List<String> addresses = instances.stream()
                                          .map(ServiceInstance::getAddress)
                                          .collect(Collectors.toList());

        if (addresses.isEmpty()) {
            ctx.status(404).json(List.of());
        } else {
            ctx.json(addresses);
        }
    }

    private void viewRegistry(Context ctx) {
        ctx.json(registryService.getRegistry());
    }
}