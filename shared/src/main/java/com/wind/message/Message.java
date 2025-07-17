package com.wind.message;

import com.wind.entities.WeatherData;
import com.wind.message.Huffman.ArvoreHuffman;
import com.wind.entities.MicrocontrollerEntity;

import java.util.Date;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;

public class Message implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    
    public String content;
    private ArvoreHuffman arvore;
    private boolean isCompressed;
    

    public Message(String instrucao) {
        this.content = instrucao;
        this.arvore = new ArvoreHuffman();

        // Cria a árvore de Huffman
        char[] caracteres = new char[content.length()];
        int[] frequencias = new int[content.length()];

        arvore.contarCaractereFrequencia(content, caracteres, frequencias);
        arvore.construirArvore(caracteres, frequencias);
        arvore.imprimirCodigos();

        // Comprime a mensagem
        this.content = arvore.comprimir(content);
        this.isCompressed = true;
    }


    public Message(WeatherData weather, String instrucao) {
        this.content = weather.getId() + "|" + weather.getMicrocontroller().getId() + "|" + weather.getMicrocontroller().getRegion() + "|" + weather.getTime() + "|" +
                weather.getPressure() + "|" + weather.getRadiation() + "|" + weather.getTemperature() + "|" + weather.getHumidity() + "|" + instrucao;
        this.arvore = new ArvoreHuffman();

        // Cria a árvore de Huffman
        char[] caracteres = new char[content.length()];
        int[] frequencias = new int[content.length()];

        arvore.contarCaractereFrequencia(content, caracteres, frequencias);
        arvore.construirArvore(caracteres, frequencias);
        arvore.imprimirCodigos();

        // Comprime a mensagem
        this.content = arvore.comprimir(content);
        this.isCompressed = true;
    }


    public Message(int codigo, String instrucao) {
        this.content = codigo + "|" + instrucao;
        this.arvore = new ArvoreHuffman();

        // Cria a árvore de Huffman
        char[] caracteres = new char[content.length()];
        int[] frequencias = new int[content.length()];

        arvore.contarCaractereFrequencia(content, caracteres, frequencias);
        arvore.construirArvore(caracteres, frequencias);
        arvore.imprimirCodigos();

        // Comprime a mensagem
        this.content = arvore.comprimir(content);
        this.isCompressed = true;
    }


    public Message(WeatherData[] weathers, String instrucao) {
        this.content = "";
        for (WeatherData weather : weathers) {
            if (weather != null) {
                this.content += weather.getId() + "|" + weather.getMicrocontroller().getId() + "|" + weather.getMicrocontroller().getRegion() + "|" +
                        weather.getTime() + "|" + weather.getPressure() + "|" + weather.getRadiation() + "|" +
                        weather.getTemperature() + "|" + weather.getHumidity() + "|";
            }
        }

        this.content += instrucao;
        //this.arvore = new ArvoreHuffman();

        // Cria a árvore de Huffman
        //char[] caracteres = new char[content.length()];
        //int[] frequencias = new int[content.length()];

        //arvore.contarCaractereFrequencia(content, caracteres, frequencias);
        //arvore.construirArvore(caracteres, frequencias);
        //arvore.imprimirCodigos();

        // Comprime a mensagem
        //this.content = arvore.comprimir(content);
        this.isCompressed = false;
    }


    // Construtor para MicrocontrollerEntity
    public Message(MicrocontrollerEntity microcontroller, String instrucao) {
        this.content = microcontroller.getId() + "|" + microcontroller.getRegion() + "|" + instrucao;

        this.arvore = new ArvoreHuffman();

        // Cria a árvore de Huffman
        char[] caracteres = new char[content.length()];
        int[] frequencias = new int[content.length()];

        arvore.contarCaractereFrequencia(content, caracteres, frequencias);
        arvore.construirArvore(caracteres, frequencias);
        arvore.imprimirCodigos();

        // Comprime a mensagem
        this.content = arvore.comprimir(content);
        this.isCompressed = true;
    }

    public Message(Integer ID, String instrucao) {
        this.content = ID.toString() + "|" + instrucao;
        this.arvore = new ArvoreHuffman();

        // Cria a árvore de Huffman
        char[] caracteres = new char[content.length()];
        int[] frequencias = new int[content.length()];

        arvore.contarCaractereFrequencia(content, caracteres, frequencias);
        arvore.construirArvore(caracteres, frequencias);
        arvore.imprimirCodigos();

        // Comprime a mensagem
        this.content = arvore.comprimir(content);
        this.isCompressed = true;
    }


    // Getters  
    public WeatherData getWeatherData() throws ParseException {
        // Checa se a mensagem está comprimida
        if (isCompressed) this.content = arvore.descomprimir(this.content);
        isCompressed = false;
        
        // splited array
        String[] splited = this.content.split("[|]");

        //this.content = weather.getId() + "|" + weather.getMicrocontroller().getId() + "|" + weather.getMicrocontroller().getLocation() + "|" + weather.getTime() + "|" +
        //    weather.getPressure() + "|" + weather.getRadiation() + "|" + weather.getTemperature() + "|" + weather.getHumidity() + "|" + instrucao;

        int ID = Integer.parseInt(splited[0]);
        int microcontrollerID = Integer.parseInt(splited[1]);
        
        String location = splited[2];
        
        SimpleDateFormat formatter = new SimpleDateFormat("EEE MMM d HH:mm:ss zzz yyyy", Locale.ENGLISH);
        Date time = formatter.parse((String)splited[3]);
        
        float pressure = Float.parseFloat(splited[4]);
        float radiation = Float.parseFloat(splited[5]);
        float temperature = Float.parseFloat(splited[6]);
        float humidity = Float.parseFloat(splited[7]);

        MicrocontrollerEntity microcontroller = new MicrocontrollerEntity(microcontrollerID, location);
        WeatherData weather = new WeatherData(ID, microcontroller, time, WeatherData.getCount(), pressure, radiation, temperature, humidity);

        return weather;
    }

    public WeatherData[] getWeathers() throws ParseException {
        // Checa se a mensagem está comprimida
        if (isCompressed) this.content = arvore.descomprimir(this.content);
        isCompressed = false;

        // splited array
        String[] splited = this.content.split("[|]");

        WeatherData[] weathers = new WeatherData[splited.length / 8];
        int j = 0;
        
        if (weathers.length == 0) return weathers;

        for (int i = 0; i < splited.length - 1; i += 8) {
            // Transforma a string em um objeto WeatherData
            int ID = Integer.parseInt(splited[i]);
            int microcontrollerID = Integer.parseInt(splited[i + 1]);
            String location = splited[i + 2];

            // Parse data e.g.: Thu Oct 24 23:34:10 BRT 2024
            SimpleDateFormat formatter = new SimpleDateFormat("EEE MMM d HH:mm:ss zzz yyyy", Locale.ENGLISH);
            Date time = formatter.parse(splited[i + 3]);

            float pressure = Float.parseFloat(splited[i + 4]);
            float radiation = Float.parseFloat(splited[i + 5]);
            float temperature = Float.parseFloat(splited[i + 6]);
            float humidity = Float.parseFloat(splited[i + 7]);

            MicrocontrollerEntity microcontroller = new MicrocontrollerEntity(microcontrollerID, location);

            // Passa .getCount() para que a criação desse objeto não interfira na contagem do database
            weathers[j] = new WeatherData(ID, microcontroller, time, WeatherData.getCount(), pressure, radiation, temperature, humidity);
            j++;
        }

        return weathers;
    }

    public int getCodigo() {
        // Checa se a mensagem está comprimida
        if (isCompressed) {
            this.content = arvore.descomprimir(this.content);
        }
        
        isCompressed = false;

        return Integer.parseInt(this.content.split("[|]")[0]);
    }
    
    public MicrocontrollerEntity getMicrocontrollerEntity() {
        // Checa se a mensagem está comprimida
        if (isCompressed) this.content = arvore.descomprimir(this.content);
        isCompressed = false;

        // splited array
        String[] splited = this.content.split("[|]");

        // Transforma a string em um objeto MicrocontrollerEntity
        int microcontrollerID = Integer.parseInt(splited[0]);
        String location = splited[1];

        // Cria o objeto MicrocontrollerEntity
        MicrocontrollerEntity microcontroller = new MicrocontrollerEntity(microcontrollerID, location);
        return microcontroller;
    }
    
    public String getInstrucao() {
        // Checa se a mensagem está comprimida
        if (isCompressed) this.content = arvore.descomprimir(this.content);
        isCompressed = false;

        String[] spplited_array = this.content.split("[|]");

        // Last element will be the instruction
        return spplited_array[spplited_array.length - 1];
    }

    public String getCPF() {
        // Checa se a mensagem está comprimida
        if (isCompressed) this.content = arvore.descomprimir(this.content);
        isCompressed = false;

        // splited array
        String[] splited = this.content.split("[|]");

        return splited[0];
    } 
}
