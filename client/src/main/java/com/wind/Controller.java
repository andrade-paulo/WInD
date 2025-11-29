package com.wind;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.wind.entities.WeatherData;
import com.wind.entities.ClientEntity;
import com.wind.security.RSA;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.time.Duration;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.util.Base64;

public class Controller {
    private static HttpClient httpClient;
    private static ObjectMapper objectMapper;
    private static String baseUrl;
    private static String apiKey;
    private static SecretKey aesKey;

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
        try {
            performHandshake();
        } catch (Exception e) {
            System.err.println("Falha no handshake de segurança: " + e.getMessage());
        }
    }

    private static void performHandshake() throws Exception {
        // 1. Get Server Public Key
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/app/security/public-key"))
                .header("X-API-Key", apiKey)
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new Exception("Failed to retrieve public key");
        }
        
        PublicKey serverPublicKey = RSA.getPublicKeyFromBase64(response.body());

        // 2. Generate AES Key
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256);
        aesKey = keyGen.generateKey();

        // 3. Encrypt AES Key with RSA
        String encryptedAesKey = RSA.encrypt(aesKey.getEncoded(), serverPublicKey);

        // 4. Send Encrypted AES Key to Server
        request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/app/security/handshake"))
                .header("X-API-Key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(encryptedAesKey))
                .build();
        
        response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new Exception("Handshake failed");
        }
        System.out.println("Handshake de segurança realizado com sucesso.");
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

    public static boolean login(String username, String password) throws Exception {
        ClientEntity credentials = new ClientEntity(username, password);
        String json = objectMapper.writeValueAsString(credentials);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/app/auth/login"))
                .header("Content-Type", "application/json")
                .header("X-API-Key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        return response.statusCode() == 200;
    }

    public static boolean register(String username, String password) throws Exception {
        ClientEntity credentials = new ClientEntity(username, password);
        String json = objectMapper.writeValueAsString(credentials);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/app/auth/register"))
                .header("Content-Type", "application/json")
                .header("X-API-Key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        return response.statusCode() == 201;
    }
}