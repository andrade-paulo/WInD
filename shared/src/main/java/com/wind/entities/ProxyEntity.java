package com.wind.entities;

import java.io.Serializable;
import java.util.Objects;

public class ProxyEntity implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String address;
    private int port; 
    
    private String multicastAddress;
    private int multicastPort;
    
    private int heartbeatPort;
    private int rmiReplicaPort;

    public ProxyEntity(String address, int port, int heartbeatPort, int rmiReplicaPort, String multicastAddress, int multicastPort) {
        this.address = address;
        this.port = port;
        this.heartbeatPort = heartbeatPort;
        this.rmiReplicaPort = rmiReplicaPort;
        this.multicastAddress = multicastAddress;
        this.multicastPort = multicastPort;
    }

    public String getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }

    public int getHeartbeatPort() {
        return heartbeatPort;
    }

    public int getRmiReplicaPort() {
        return rmiReplicaPort;
    }

    public String getMulticastAddress() {
        return multicastAddress;
    }

    public int getMulticastPort() {
        return multicastPort;
    }

    @Override
    public String toString() {
        return "ProxyEntity{" +
                "address='" + address + '\'' +
                ", port=" + port +
                ", heartbeatPort=" + heartbeatPort +
                ", rmiReplicaPort=" + rmiReplicaPort +
                ", multicastAddress='" + multicastAddress + '\'' +
                ", multicastPort=" + multicastPort +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProxyEntity that = (ProxyEntity) o;
        return port == that.port &&
               heartbeatPort == that.heartbeatPort &&
               rmiReplicaPort == that.rmiReplicaPort &&
               Objects.equals(address, that.address);
    }
}