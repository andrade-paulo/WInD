package com.wind;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.wind.entities.WeatherData;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class Controller {
    private static HttpClient httpClient;
    private static ObjectMapper objectMapper;
    private static String baseUrl;
    private static String apiKey;

    // Bloco inicializador para configurar o cliente HTTP e o ObjectMapper
    static {
        httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        objectMapper = new ObjectMapper();

        // Configuração para ignorar propriedades desconhecidas ao desserializar
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public static void init(String gatewayUrl, String key) {
        Controller.baseUrl = gatewayUrl;
        Controller.apiKey = key;
    }

    /**
     * Busca dados climáticos de um microcontrolador específico.
     * @param codigo O ID do microcontrolador.
     * @param region A região do microcontrolador.
     * @return Um array de WeatherData.
     * @throws Exception Se ocorrer um erro na comunicação ou processamento.
     */
    public static WeatherData[] getWeatherByMicrocontroller(int codigo, String region) throws Exception {
        String uriString = String.format("%s/app/weather/microcontroller/%d?region=%s", baseUrl, codigo, region);
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uriString))
                .header("X-API-Key", apiKey)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            WeatherData[] weatherData = objectMapper.readValue(response.body(), WeatherData[].class);
            System.out.println("\nEncontrados " + weatherData.length + " registros para o microcontrolador " + codigo + " na região " + region);
            return weatherData;
        } else {
            throw new Exception("Erro ao buscar dados: " + response.statusCode() + " - " + response.body());
        }
    }

    /**
     * Busca todos os dados climáticos disponíveis.
     * @return Um array de WeatherData.
     * @throws Exception Se ocorrer um erro na comunicação ou processamento.
     */
    public static WeatherData[] getAllWeatherDatas() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/app/weather"))
                .header("X-API-Key", apiKey)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return objectMapper.readValue(response.body(), WeatherData[].class);
        } else {
            throw new Exception("Erro ao buscar dados: " + response.statusCode() + " - " + response.body());
        }
    }
}