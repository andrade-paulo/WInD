package com.wind.model.DAO;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;

import com.wind.datastructures.Hash;
import com.wind.entities.ClientEntity;
import com.wind.security.AES;
import com.wind.security.KeyStoreManager;
import com.wind.security.PasswordManager;

public class ClientDAO {
    private Hash<ClientEntity> clientHash;
    private int ocupacao;
    private AES aes;

    private final String ARQUIVO = "/app/database/clients.dat";
    private final int TAMANHO_INICIAL = 100;

    public ClientDAO() {
        KeyStoreManager keyStoreManager = new KeyStoreManager();
        this.aes = new AES(keyStoreManager.getSecretKey());

        clientHash = new Hash<>(TAMANHO_INICIAL);
        loadDiskDatabase();
        ocupacao = clientHash.getOcupacao();
    }

    public ClientEntity[] selectAll() {
        LogDAO.addLog("[DB SELECT] Selecionando todos os clientes");
        List<ClientEntity> list = clientHash.getAll();
        return list.toArray(new ClientEntity[0]);
    }

    public void addClient(ClientEntity client) {
        client.setId(clientHash.getOcupacao() + 1);
        
        // Hash password before storing
        String hashedPassword = PasswordManager.hashPassword(client.getPassword());
        client.setPassword(hashedPassword);
        
        clientHash.inserir(client.getId(), client);
        ocupacao++;
        updateArquivo();
    }

    public ClientEntity getClient(int id) {
        ClientEntity client = clientHash.buscar(id);
        if (client == null) {
            LogDAO.addLog("[DB MISS] Cliente " + id + " não encontrado");
            return null;
        }
        LogDAO.addLog("[DB HIT] Cliente " + id + " encontrado");
        return client;
    }

    public boolean updateClient(ClientEntity client) {
        if (clientHash.buscar(client.getId()) == null) {
            LogDAO.addLog("[DB MISS] Cliente " + client.getId() + " não encontrado");
            return false;
        }
        clientHash.inserir(client.getId(), client);
        LogDAO.addLog("[DB UPDATE] Cliente " + client.getId() + " atualizado");
        updateArquivo();
        return true;
    }

    public ClientEntity deleteClient(int id) {
        try {
            ClientEntity client = clientHash.remover(id);
            ocupacao--;
            LogDAO.addLog("[DB DELETE] Cliente " + id + ", ocupação: " + ocupacao + "/" + clientHash.getTamanho());
            updateArquivo();
            return client;
        } catch (Exception e) {
            LogDAO.addLog("[DB MISS] Cliente " + id + " não encontrado");
            return null;
        }
    }
    
    public ClientEntity authenticate(String username, String password) {
        ClientEntity[] clients = selectAll();
        for (ClientEntity client : clients) {
            if (client.getUsername().equals(username)) {
                if (PasswordManager.verifyPassword(password, client.getPassword())) {
                    LogDAO.addLog("[AUTH SUCCESS] Cliente " + username + " autenticado");
                    return client;
                }
            }
        }
        LogDAO.addLog("[AUTH FAIL] Falha na autenticação para " + username);
        return null;
    }

    public boolean exists(String username) {
        ClientEntity[] clients = selectAll();
        for (ClientEntity client : clients) {
            if (client.getUsername().equals(username)) {
                return true;
            }
        }
        return false;
    }

    private void updateArquivo() {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream out = new ObjectOutputStream(baos);
            out.writeObject(clientHash);
            out.close();
            
            byte[] encryptedData = aes.encrypt(baos.toByteArray());
            
            FileOutputStream file = new FileOutputStream(ARQUIVO);
            file.write(encryptedData);
            file.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    public void loadDiskDatabase() {
        try {
            File file = new File(ARQUIVO);
            if (!file.exists()) {
                File parent = file.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                file.createNewFile();
            } else {
                FileInputStream fileIn = new FileInputStream(ARQUIVO);
                byte[] fileContent = fileIn.readAllBytes();
                fileIn.close();
                
                if (fileContent.length > 0) {
                    byte[] decryptedData = aes.decrypt(fileContent);
                    if (decryptedData != null) {
                        ByteArrayInputStream bais = new ByteArrayInputStream(decryptedData);
                        ObjectInputStream objectIn = new ObjectInputStream(bais);
                        clientHash = (Hash<ClientEntity>) objectIn.readObject();
                        objectIn.close();
                    } else {
                        System.err.println("Falha ao descriptografar o banco de dados de clientes.");
                        clientHash = new Hash<>(TAMANHO_INICIAL);
                    }
                }
            }
        } catch (IOException e) {
            clientHash = new Hash<>(TAMANHO_INICIAL);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }
}
