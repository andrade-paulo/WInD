package com.wind.entities;

import java.io.Serializable;

public class MicrocontrollerEntity implements Serializable {
    private int id;
    private String ip;
    private int port;
    private String region;

    public MicrocontrollerEntity() {}

    public MicrocontrollerEntity(int id, String location) {
        this.id = id;
        this.region = location;
    }

    public MicrocontrollerEntity(int id, String ip, int port, String region) {
        this.id = id;
        this.ip = ip;
        this.port = port;
        this.region = region;
    }

    public int getId() {
        return id;
    }

    public String getRegion() {
        return region;
    }

    public String getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String toString() {
        return "ID: " + id + "\nLocation: " + region;
    }

    public boolean equals(MicrocontrollerEntity microcontroller) {
        return this.region.equals(microcontroller.getRegion()) && this.id == microcontroller.getId();
    }
}
