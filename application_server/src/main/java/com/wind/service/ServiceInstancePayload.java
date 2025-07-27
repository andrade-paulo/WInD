package com.wind.service;

public class ServiceInstancePayload {
    private String serviceName;
    private String instanceId;
    private String address;
    private String healthCheckUrl;

    // Construtor, Getters e Setters
    public ServiceInstancePayload(String serviceName, String instanceId, String address, String healthCheckUrl) {
        this.serviceName = serviceName;
        this.instanceId = instanceId;
        this.address = address;
        this.healthCheckUrl = healthCheckUrl;
    }

    public String getServiceName() { return serviceName; }
    public String getInstanceId() { return instanceId; }
    public String getAddress() { return address; }
    public String getHealthCheckUrl() { return healthCheckUrl; }
}