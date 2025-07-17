package com.wind;

import com.wind.entities.WeatherData;


public class View {
    public View() {}

    public static void showMenu() throws Exception {
        Client.scanner.nextLine();
        clearScreen();
        String escolha = "";
        
        do {
            System.out.println(Color.CYAN + "Menu principal" + Color.RESET);
            System.out.println("1. Listar todas os dados climáticos");
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
                    escolha = "0";
                    break;
                default:
                    System.out.println(Color.RED + "Opção inválida. Tente novamente." + Color.RESET);
                    break;
            }
            
            System.out.println();
        } while (escolha != "0");
    }


    private static void listAllWeatherData() throws Exception {
        clearScreen();

        WeatherData[] allWeatherData = Controller.getAllWeatherDatas();

        if (allWeatherData.length == 0) {
            System.out.println(Color.RED + "Oops! Nenhum dado climático encontrado." + Color.RESET);
            return;
        } else if(allWeatherData.length == 1) {
            System.out.println(Color.CYAN + "1 dado climático encontrado:" + Color.RESET);
        } else {
            System.out.println(Color.CYAN + allWeatherData.length + " dados climáticos encontrados:" + Color.RESET);
        }

        for (WeatherData weatherData : allWeatherData) {
            System.out.println(weatherData + "\n");
        }
    }


    private static void searchMicrocontroller() throws Exception {
        clearScreen();

        System.out.print("Código do Microcontrolador: ");
        int microcontrollerID = Client.scanner.nextInt();
        Client.scanner.nextLine();
        
        System.out.print("Região do Microcontrolador: ");
        String microcontrollerRegion = Client.scanner.nextLine();

        WeatherData[] mcWeatherData = Controller.getWeatherByMicrocontroller(microcontrollerID, microcontrollerRegion);

        if (mcWeatherData.length == 0) {
            System.out.println(Color.RED + "Oops! Nenhum dado climático encontrado." + Color.RESET);
            return;
        } else if(mcWeatherData.length == 1) {
            System.out.println(Color.CYAN + "1 dado climático encontrado:" + Color.RESET);
        } else {
            System.out.println(Color.CYAN + mcWeatherData.length + " dados climáticos encontrados:" + Color.RESET);
        }

        for (WeatherData weatherData : mcWeatherData) {
            System.out.println(weatherData + "\n");
        }
    }

    public static void clearScreen() {
        System.out.print("\033[H\033[2J");
        System.out.flush(); 
    }
}