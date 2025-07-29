package com.wind;

import java.util.Arrays;
import com.wind.entities.WeatherData;

public class View {
    public View() {}

    public static void showMenu() {
        clearScreen();
        String escolha;
        
        do {
            System.out.println(Color.CYAN + "Menu principal" + Color.RESET);
            System.out.println("1. Listar todos os dados climáticos");
            System.out.println("2. Buscar dados climáticos por microcontrolador");
            System.out.println("0. Sair do sistema");
            System.out.print("Escolha uma opção: ");
            
            escolha = Client.scanner.nextLine();
            
            switch (escolha) {
                case "1":
                    listAllWeatherData();
                    break;
                case "2":
                    searchMicrocontroller();
                    break;
                case "0":
                    System.out.println("Saindo do sistema...");
                    break;
                default:
                    System.out.println(Color.RED + "Opção inválida. Tente novamente." + Color.RESET);
                    break;
            }
            
            if (!escolha.equals("0")) {
                System.out.println("\nPressione Enter para continuar...");
                Client.scanner.nextLine();
                clearScreen();
            }

        } while (!escolha.equals("0"));
    }


    private static void listAllWeatherData() {
        clearScreen();
        try {
            WeatherData[] allWeatherData = Controller.getAllWeatherDatas();

            if (allWeatherData == null || allWeatherData.length == 0) {
                System.out.println(Color.YELLOW + "Nenhum dado climático encontrado." + Color.RESET);
                return;
            } else if (allWeatherData.length == 1) {
                System.out.println(Color.CYAN + "1 dado climático encontrado:" + Color.RESET);
            } else {
                System.out.println(Color.CYAN + allWeatherData.length + " dados climáticos encontrados:" + Color.RESET);
            }

            for (WeatherData weatherData : allWeatherData) {
                System.out.println(weatherData + "\n");
            }

            double avgTemperature = Arrays.stream(allWeatherData).mapToDouble(WeatherData::getTemperature).average().orElse(0.0);
            double avgHumidity = Arrays.stream(allWeatherData).mapToDouble(WeatherData::getHumidity).average().orElse(0.0);
            double avgPressure = Arrays.stream(allWeatherData).mapToDouble(WeatherData::getPressure).average().orElse(0.0);
            double avgRadiation = Arrays.stream(allWeatherData).mapToDouble(WeatherData::getRadiation).average().orElse(0.0);
            
            System.out.println(Color.YELLOW + "===================================" + Color.RESET);
            System.out.println(Color.YELLOW + "      MÉDIAS GERAIS DOS DADOS      " + Color.RESET);
            System.out.println(Color.YELLOW + "===================================" + Color.RESET);
            System.out.printf("Temperatura Média: %.2f K%n", avgTemperature);
            System.out.printf("Umidade Média    : %.2f %%%n", avgHumidity);
            System.out.printf("Pressão Média     : %.2f hPa%n", avgPressure);
            System.out.printf("Radiação Média   : %.2f W/m²%n", avgRadiation);
            System.out.println(Color.YELLOW + "===================================" + Color.RESET);

        } catch (Exception e) {
            System.out.println(Color.RED + "Erro ao buscar dados climáticos: " + e.getMessage() + Color.RESET);
        }
    }


    private static void searchMicrocontroller() {
        clearScreen();
        try {
            System.out.print("Código do Microcontrolador: ");
            int microcontrollerID = Integer.parseInt(Client.scanner.nextLine());
            
            System.out.print("Região do Microcontrolador: ");
            String microcontrollerRegion = Client.scanner.nextLine();

            WeatherData[] mcWeatherData = Controller.getWeatherByMicrocontroller(microcontrollerID, microcontrollerRegion);

            if (mcWeatherData == null || mcWeatherData.length == 0) {
                System.out.println(Color.YELLOW + "Nenhum dado climático encontrado para este microcontrolador." + Color.RESET);
                return;
            } else if(mcWeatherData.length == 1) {
                System.out.println(Color.CYAN + "1 dado climático encontrado:" + Color.RESET);
            } else {
                System.out.println(Color.CYAN + mcWeatherData.length + " dados climáticos encontrados:" + Color.RESET);
            }

            for (WeatherData weatherData : mcWeatherData) {
                if (weatherData != null)
                    System.out.println(weatherData + "\n");
            }

            double avgTemperature = Arrays.stream(mcWeatherData).mapToDouble(WeatherData::getTemperature).average().orElse(0.0);
            double avgHumidity = Arrays.stream(mcWeatherData).mapToDouble(WeatherData::getHumidity).average().orElse(0.0);
            double avgPressure = Arrays.stream(mcWeatherData).mapToDouble(WeatherData::getPressure).average().orElse(0.0);
            double avgRadiation = Arrays.stream(mcWeatherData).mapToDouble(WeatherData::getRadiation).average().orElse(0.0);
            
            System.out.println(Color.YELLOW + "===================================" + Color.RESET);
            System.out.println(Color.YELLOW + "      MÉDIAS DOS DADOS DO MC       " + Color.RESET);
            System.out.println(Color.YELLOW + "===================================" + Color.RESET);
            System.out.printf("Temperatura Média: %.2f K%n", avgTemperature);
            System.out.printf("Umidade Média    : %.2f %%%n", avgHumidity);
            System.out.printf("Pressão Média     : %.2f hPa%n", avgPressure);
            System.out.printf("Radiação Média   : %.2f W/m²%n", avgRadiation);
            System.out.println(Color.YELLOW + "===================================" + Color.RESET);
        } catch (NumberFormatException e) {
            System.out.println(Color.RED + "Código do microcontrolador inválido. Por favor, insira um número." + Color.RESET);
        } catch (Exception e) {
            System.out.println(Color.RED + "Erro ao buscar dados: " + e.getMessage() + Color.RESET);
        }
    }

    public static void clearScreen() {
        System.out.print("\033[H\033[2J");
        System.out.flush(); 
    }
}