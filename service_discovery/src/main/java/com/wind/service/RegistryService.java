package com.wind.service;

import com.wind.model.ServiceInstance;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class RegistryService {

    // Usamos ConcurrentHashMap para garantir que o acesso ao mapa seja thread-safe.
    // A chave do mapa externo é o nome do serviço (ex: "application-server").
    // O mapa interno guarda as instâncias por instanceId.
    private final Map<String, Map<String, ServiceInstance>> registry = new ConcurrentHashMap<>();

    public void register(ServiceInstance instance) {
        instance.setLastHeartbeat(Instant.now());
        // Se o serviço ainda não existe no registro, cria um novo mapa para ele.
        registry.computeIfAbsent(instance.getServiceName(), k -> new ConcurrentHashMap<>())
                .put(instance.getInstanceId(), instance);
        System.out.println("Instância registrada: " + instance.getInstanceId());
    }

    public void deregister(String serviceName, String instanceId) {
        if (registry.containsKey(serviceName)) {
            registry.get(serviceName).remove(instanceId);
            System.out.println("Instância desregistrada: " + instanceId);
            // Se não houver mais instâncias, remove o serviço do registro
            if (registry.get(serviceName).isEmpty()) {
                registry.remove(serviceName);
            }
        }
    }

    public List<ServiceInstance> getHealthyInstances(String serviceName) {
        Map<String, ServiceInstance> instances = registry.get(serviceName);
        if (instances == null) {
            return List.of(); // Retorna lista vazia se o serviço não existe
        }
        // Na nossa implementação de health check, vamos simplesmente remover instâncias inativas.
        // Então, todas as instâncias no registro são consideradas saudáveis.
        return instances.values().stream().collect(Collectors.toList());
    }
    
    public Map<String, Map<String, ServiceInstance>> getRegistry() {
        return registry;
    }
}