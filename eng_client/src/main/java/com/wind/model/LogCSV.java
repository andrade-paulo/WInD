package com.wind.model;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;

public class LogCSV {

    private final String filePath;
    private BufferedWriter writer;

    private static final String CSV_HEADER = "Timestamp,Topic,ID,Region,Pressure,Radiation,Temperature,Humidity\n";

    public LogCSV() {
        // Cria um nome de arquivo único com base na data e hora atuais
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
        String timestamp = LocalDateTime.now().format(formatter);
        this.filePath = "wind_log_" + timestamp + ".csv";
        
        try {
            this.writer = new BufferedWriter(new FileWriter(this.filePath, true));
            System.out.println("[LogCSV] Arquivo de log criado em: " + this.filePath);
            
            // Escreve o cabeçalho no arquivo
            this.writer.write(CSV_HEADER);

        } catch (IOException e) {
            System.err.println("Erro ao criar o arquivo de log CSV: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public synchronized void log(String topic, String data) {
        if (writer == null) {
            System.err.println("[LogCSV] Não é possível registrar, o logger não foi inicializado corretamente.");
            return;
        }

        try {
            String timestamp = new Timestamp(System.currentTimeMillis()).toString();

            // Transformar o data 5|North|1036.31|1348.56|275.43|3.59
            String[] parts = data.split("\\|");
            
            String id = parts.length > 0 ? parts[0] : "N/A";
            String region = parts.length > 1 ? parts[1] : "N/A";
            String pressure = parts.length > 2 ? parts[2] : "N/A";
            String radiation = parts.length > 3 ? parts[3] : "N/A";
            String temperature = parts.length > 4 ? parts[4] : "N/A";
            String humidity = parts.length > 5 ? parts[5] : "N/A";
            
            // Monta a linha do CSV. As aspas duplas em volta dos dados evitam problemas se houver vírgulas.
            String csvLine = String.format("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"\n",
                    timestamp, topic, id, region, pressure, radiation, temperature, humidity);
            
            writer.write(csvLine);
            writer.flush(); // Garante que a linha seja escrita no disco imediatamente

        } catch (IOException e) {
            System.err.println("Erro ao escrever no arquivo de log CSV: " + e.getMessage());
        }
    }

    public void close() {
        if (writer != null) {
            try {
                System.out.println("[LogCSV] Fechando o arquivo de log...");
                writer.close();
            } catch (IOException e) {
                System.err.println("Erro ao fechar o arquivo de log CSV: " + e.getMessage());
            }
        }
    }
}