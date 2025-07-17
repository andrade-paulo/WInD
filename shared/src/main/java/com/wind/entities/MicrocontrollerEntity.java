package com.wind.entities;

import java.io.Serializable;

public class MicrocontrollerEntity implements Serializable {
    private int id;
    private String region;

    public MicrocontrollerEntity(int id, String location) {
        this.id = id;
        this.region = location;
    }

    public int getId() {
        return id;
    }

    public String getRegion() {
        return region;
    }

    public String toString() {
        return "ID: " + id + "\nLocation: " + region;
    }

    public boolean equals(MicrocontrollerEntity microcontroller) {
        return this.region.equals(microcontroller.getRegion()) && this.id == microcontroller.getId();
    }
}
