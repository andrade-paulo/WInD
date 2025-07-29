package com.wind.model.DAO;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import com.wind.datastructures.Hash;
import com.wind.entities.WeatherData;
import com.wind.entities.MicrocontrollerEntity;


public class WeatherDataDAO {
    private Hash<WeatherData> weatherDataHash;
    private int ocupacao;

    //private final String ARQUIVO = "database/database.dat";
    private final String ARQUIVO = "/app/database/database.dat";
    private final int TAMANHO_INICIAL = 100; 
    
    public WeatherDataDAO() {
        weatherDataHash = new Hash<>(TAMANHO_INICIAL);
        loadDiskDatabase();
        ocupacao = weatherDataHash.getOcupacao();
    }


    public WeatherData[] selectAll() {
        LogDAO.addLog("[DB SELECT] Selecionando todas as informações climáticas");

        return weatherDataHash.getAllWeatherData();
    }


    public WeatherData[] selectByMicrocontroller(MicrocontrollerEntity microcontroller) {
        LogDAO.addLog("[DB SELECT] Selecionando informações climáticas do microcontrolador " + microcontroller.getId());
        return weatherDataHash.getWeatherByMicrocontroller(microcontroller);
    }


    public void addWeatherData(WeatherData weather) {
        weather.setId(weatherDataHash.getOcupacao() + 1);
        weatherDataHash.inserir(weather.getId(), weather);
        ocupacao++;

        //LogDAO.addLog("[DB INSERT] Novo registro climático " + weather.getId() + ", ocupação: " + ocupacao + "/" + weatherDataHash.getTamanho());
        
        updateArquivo();
    }


    public WeatherData getWeatherData(int codigo) {
        WeatherData weather = weatherDataHash.buscar(codigo);
        
        if (weather == null) {
            LogDAO.addLog("[DB MISS] Registro climático " + codigo + " não encontrado");
            return null;
        }

        LogDAO.addLog("[DB HIT] Registro climático " + codigo + " encontrado");
        return weather;
    }


    public boolean updateWeatherData(WeatherData weather) {
        if (weatherDataHash.buscar(weather.getId()) == null) {
            LogDAO.addLog("[DB MISS] Registro climático " + weather.getId() + " não encontrado");
            return false;
        }

        weatherDataHash.inserir(weather.getId(), weather);
        LogDAO.addLog("[DB UPDATE] Registro climático " + weather.getId() + " atualizado");

        updateArquivo();
        return true;
    }


    public WeatherData deleteWeatherData(int codigo) {
        try {
            WeatherData weather = weatherDataHash.remover(codigo);
            ocupacao--;
            
            LogDAO.addLog("[DB DELETE] Registro climático " + codigo + ", ocupação: " + ocupacao + "/" + weatherDataHash.getTamanho());
            
            updateArquivo();
            
            return weather;
        } catch (Exception e) {
            LogDAO.addLog("[DB MISS] Registro climático " + codigo + " não encontrada");
            return null;
        }
    }


    private void updateArquivo() {
        try {
            FileOutputStream file = new FileOutputStream(ARQUIVO);
            ObjectOutputStream out = new ObjectOutputStream(file);
            out.writeObject(weatherDataHash);
            out.close();
            file.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    

    @SuppressWarnings("unchecked")
    public void loadDiskDatabase() {
        // Carregar arquivo binário "database.dat" e preencher Hash com as OSs
        try {
            File file = new File(ARQUIVO);
            if (!file.exists()) {
                file.createNewFile();
            } else {
                FileInputStream fileIn = new FileInputStream(ARQUIVO);
                ObjectInputStream objectIn = new ObjectInputStream(fileIn);
                weatherDataHash = (Hash<WeatherData>) objectIn.readObject();
                objectIn.close();
                fileIn.close();
            }
        } catch (IOException e) {
            // Se o arquivo estiver vazio, criar uma nova HashTable
            weatherDataHash = new Hash<>(TAMANHO_INICIAL);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }


    public boolean isEmpty() {
        return ocupacao == 0;
    }

    public WeatherData getUltimo() {
        return weatherDataHash.getUltimo();
    }

    public int getOcupacao() {
        return ocupacao;
    }

    public int getTamanho() {
        return weatherDataHash.getTamanho();
    }
}
