package com.wind.model.DAO;

import java.io.*;
import java.util.Date;

public class LogDAO {
    static private String log;
    //private static final String ARQUIVO = "database/log.txt"; 
    private static final String ARQUIVO = "proxy_server/src/main/java/com/wind/database/log.txt"; 

    public LogDAO() {
        loadLog();
    }

    public static void loadLog() {
        log = "";

        // Carregar o arquivo "log.txt" ou criar um novo
        try {
            File file = new File(ARQUIVO);

            if (!file.createNewFile()) {
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(ARQUIVO), "UTF-8"));
                String line;

                while ((line = bufferedReader.readLine()) != null) {
                    log += line + "\n";
                }

                bufferedReader.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void addLog(String log) {
        Date date = new Date();

        // Format the date
        String formattedDate = String.format("%tF %tT", date, date);

        LogDAO.log += formattedDate + ": " + log + "\n";

        // Salvar no arquivo "log.txt" com utf-8
        try {
            BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(ARQUIVO), "UTF-8"));
            bufferedWriter.write(LogDAO.log);
            bufferedWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    
    public static String getLog() {
        return log;
    }


    public static String getLine(int i) {
        String[] lines = log.split("\n");

        if (i < 0 || i >= lines.length) {
            return null;
        }

        return lines[i];
    }
}
