package com.wind;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import com.wind.message.Message;
import com.wind.entities.WeatherData;
import com.wind.entities.MicrocontrollerEntity;

public class Controller {
    private static ObjectOutputStream out;
    private static ObjectInputStream in;
    
    public Controller() {}

    public static void setProxyServer(ObjectOutputStream proxyOut, ObjectInputStream proxyIn) {
        Controller.out = proxyOut;
        Controller.in = proxyIn;
    }


    public static WeatherData[] getWeatherByMicrocontroller(int codigo, String region) throws Exception {
        MicrocontrollerEntity microcontroller = new MicrocontrollerEntity(codigo, region);

        Message request = new Message(microcontroller, "SELECTBYMC");
        out.writeObject(request);
        out.flush();
        
        Message response = (Message) in.readObject();

        if (response.getInstrucao().equals("SUCCESS_SELECT_BY_MC") || response.getInstrucao().equals("EMPTY_SELECT_BY_MC")) {
            System.out.println("Found " + response.getWeathers().length + " weather data for microcontroller " + codigo + " in region " + region);
            return response.getWeathers();
        } else {
            throw new Exception("Erro ao buscar dados climáticos: " + response.getInstrucao());
        }
    }


    public static WeatherData[] getAllWeatherDatas() throws Exception {
        Message request = new Message("SELECTALL");
        out.writeObject(request);
        out.flush();
        
        Message response = (Message) in.readObject();

        if (response.getInstrucao().equals("SUCCESS_SELECT_ALL") || response.getInstrucao().equals("EMPTY_SELECT_ALL")) {
            return response.getWeathers();
        } else {
            throw new Exception("Erro ao buscar dados climáticos: " + response.getInstrucao());
        }
    }
}
