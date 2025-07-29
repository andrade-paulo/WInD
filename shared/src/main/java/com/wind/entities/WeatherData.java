package com.wind.entities;

import java.io.Serializable;
import java.util.Date;

public class WeatherData implements Serializable{
    private static int count = 0;
    private int id;
    private float pressure;
    private float radiation;
    private float temperature;
    private float humidity;
    private MicrocontrollerEntity microcontroller;
    private Date time;

    private static final long serialVersionUID = 1L;

    public WeatherData() {}

    public WeatherData(MicrocontrollerEntity microcontroller, float pressure, float radiation, float temperature, float humidity) {
        this.id = ++count;
        this.time = new Date();
        this.microcontroller = microcontroller;

        this.pressure = pressure;
        this.radiation = radiation;
        this.temperature = temperature;
        this.humidity = humidity;
    }

    public WeatherData(int id, MicrocontrollerEntity microcontroller, Date time, int count, float pressure, float radiation, float temperature, float humidity) {
        this.id = id;
        this.time = time;
        this.microcontroller = microcontroller;
        WeatherData.count = count;

        this.pressure = pressure;
        this.radiation = radiation;
        this.temperature = temperature;
        this.humidity = humidity;
    }

    // Getters
    public int getId() { return id; }
    public float getPressure() { return pressure; }
    public float getRadiation() { return radiation; }
    public float getTemperature() { return temperature; }
    public float getHumidity() { return humidity; }
    public Date getTime() { return time; }
    public MicrocontrollerEntity getMicrocontroller() { return microcontroller; }
    public static int getCount() { return count; }

    // Setters
    public void setId(int id) { this.id = id; }
    public void setPressure(float pressure) { this.pressure = pressure; }
    public void setRadiation(float radiation) { this.radiation = radiation; }
    public void setTemperature(float temperature) { this.temperature = temperature; }
    public void setHumidity(float humidity) { this.humidity = humidity; }
    public void setTime(Date time) { this.time = time; }
    public void setMicrocontroller(MicrocontrollerEntity microcontroller) { this.microcontroller = microcontroller; }
    public static void setCount(int count) { WeatherData.count = count; }

    public String toString() {
        String horaFormatada = String.format("%tF %tT", time, time);
        return "ID: " + id + "\n" +
               "Microcontroller: " + microcontroller.getId() + "\n" +
               "Region: " + microcontroller.getRegion() + "\n" +
               "Time: " + horaFormatada + "\n" +
               "Pressure: " + pressure + "\n" +
               "Radiation: " + radiation + "\n" +
               "Temperature: " + temperature + "\n" +
               "Humidity: " + humidity;
    }

    public boolean equals(WeatherData ordemServico) {
        return this.id == ordemServico.getId();
    }
}
