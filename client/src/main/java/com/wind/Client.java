package com.wind;

import java.util.Scanner;

public class Client {
    public static Scanner scanner = new Scanner(System.in);
    private static final String API_KEY = "super-secret-key-123";

    public static void main(String[] args) {
        System.out.println("\r\n" +
                "===========================================================\r\n" +
                "                  _       ______      ____                 \r\n" +
                "                 | |     / /  _/___  / __ \\               \r\n" +
                "                 | | /| / // // __ \\/ / / /               \r\n" +
                "                 | |/ |/ // // / / / /_/ /                 \r\n" +
                "                 |__/|__/___/_/ /_/_____/                  \r\n" +
                "                                                           \r\n" +
                "              Weather Information Distributor              \r\n" +
                "===========================================================\r\n");

        System.out.print("Insira a URL do API Gateway (ex: http://localhost:80): ");
        String gatewayUrl = scanner.nextLine();

        if (!gatewayUrl.toLowerCase().startsWith("http://") && !gatewayUrl.toLowerCase().startsWith("https://")) {
            gatewayUrl = "http://" + gatewayUrl;
        }

        // Inicializa o Controller com os detalhes do gateway
        Controller.init(gatewayUrl, API_KEY);
        System.out.println("\nConectando ao API Gateway em " + gatewayUrl + "...");

        try {
            // Inicia a interface do usuário
            View.showMenu();
        } catch (Exception e) {
            System.err.println(Color.RED + "Um erro inesperado ocorreu: " + e.getMessage() + Color.RESET);
        } finally {
            scanner.close();
            System.out.println("Aplicação cliente encerrada.");
        }
    }
}