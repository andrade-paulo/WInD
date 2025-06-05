package com.wind.entities;

import java.io.Serializable;

public class MicrocontrollerEntity implements Serializable {
    private int id;
    private String location;

    public MicrocontrollerEntity(int id, String location) {
        this.id = id;
        this.location = location;
    }

    public int getId() {
        return id;
    }

    public String getLocation() {
        return location;
    }

    public String toString() {
        return "ID: " + id + "\nLocation: " + location;
    }

    public boolean equals(MicrocontrollerEntity cliente) {
        return this.location.equals(cliente.getLocation());
    }
}
