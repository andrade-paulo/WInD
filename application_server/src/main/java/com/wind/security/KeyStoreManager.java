package com.wind.security;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.KeyStore;
import java.security.SecureRandom;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

public class KeyStoreManager {
    private static final String KEYSTORE_TYPE = "JCEKS";
    private static final String KEYSTORE_FILE = "/app/database/wind.jks";
    private static final String KEYSTORE_PASSWORD = "wind-keystore-pass";
    private static final String KEY_ALIAS = "db-key";
    private static final String ALGORITHM = "AES";
    private static final int KEY_SIZE = 256;

    private KeyStore keyStore;

    public KeyStoreManager() {
        try {
            keyStore = KeyStore.getInstance(KEYSTORE_TYPE);
            File file = new File(KEYSTORE_FILE);

            if (file.exists()) {
                loadKeyStore(file);
            } else {
                createKeyStore(file);
            }
        } catch (Exception e) {
            throw new RuntimeException("Erro ao inicializar KeyStore", e);
        }
    }

    private void loadKeyStore(File file) throws Exception {
        try (FileInputStream fis = new FileInputStream(file)) {
            keyStore.load(fis, KEYSTORE_PASSWORD.toCharArray());
        }
    }

    private void createKeyStore(File file) throws Exception {
        keyStore.load(null, KEYSTORE_PASSWORD.toCharArray());

        // Generate new AES Key
        KeyGenerator keyGenerator = KeyGenerator.getInstance(ALGORITHM);
        keyGenerator.init(KEY_SIZE);
        SecretKey secretKey = keyGenerator.generateKey();

        // Store Key
        KeyStore.SecretKeyEntry keyEntry = new KeyStore.SecretKeyEntry(secretKey);
        KeyStore.ProtectionParameter protectionParam = new KeyStore.PasswordProtection(KEYSTORE_PASSWORD.toCharArray());
        keyStore.setEntry(KEY_ALIAS, keyEntry, protectionParam);

        // Save KeyStore
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        
        try (FileOutputStream fos = new FileOutputStream(file)) {
            keyStore.store(fos, KEYSTORE_PASSWORD.toCharArray());
        }
    }

    public SecretKey getSecretKey() {
        try {
            return (SecretKey) keyStore.getKey(KEY_ALIAS, KEYSTORE_PASSWORD.toCharArray());
        } catch (Exception e) {
            throw new RuntimeException("Erro ao recuperar chave do KeyStore", e);
        }
    }
}
