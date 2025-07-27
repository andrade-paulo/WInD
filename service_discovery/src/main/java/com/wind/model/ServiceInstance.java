package com.wind.model;

import java.time.Instant;
import java.util.Objects;

public class ServiceInstance {
    private String serviceName;
    private String instanceId;
    private String address; // Formato "ip:porta"
    private String healthCheckUrl;
    private Instant lastHeartbeat;

    // Getters e Setters
    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
    public String getInstanceId() { return instanceId; }
    public void setInstanceId(String instanceId) { this.instanceId = instanceId; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getHealthCheckUrl() { return healthCheckUrl; }
    public void setHealthCheckUrl(String healthCheckUrl) { this.healthCheckUrl = healthCheckUrl; }
    public Instant getLastHeartbeat() { return lastHeartbeat; }
    public void setLastHeartbeat(Instant lastHeartbeat) { this.lastHeartbeat = lastHeartbeat; }

    // Construtor padrão para deserialização JSON
    public ServiceInstance() {
        this.lastHeartbeat = Instant.now();
    }
    
    // equals e hashCode para permitir a comparação de instâncias
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ServiceInstance that = (ServiceInstance) o;
        return Objects.equals(serviceName, that.serviceName) &&
               Objects.equals(instanceId, that.instanceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serviceName, instanceId);
    }
}