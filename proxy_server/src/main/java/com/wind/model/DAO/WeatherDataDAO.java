package com.wind.model.DAO;

import com.wind.CO.ApplicationServerHandler;
import com.wind.entities.WeatherData;

public class WeatherDataDAO {
    private final ApplicationServerHandler applicationServerCommunicator;

    public WeatherDataDAO(ApplicationServerHandler communicator) {
        this.applicationServerCommunicator = communicator;
    }

    public void processStationData(WeatherData data) {
        applicationServerCommunicator.sendWeatherDataToAppServer(data);
    }
}