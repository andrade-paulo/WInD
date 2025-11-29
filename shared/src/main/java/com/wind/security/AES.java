package com.wind.security;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

public class AES {
    
    private SecretKey key;
    private final String CIPHER_INSTANCE = "AES/CBC/PKCS5Padding";
    private final int IV_LENGTH = 16;

    public AES(SecretKey key) {
        this.key = key;
    }

    public byte[] encrypt(byte[] data) {
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_INSTANCE);
            
            // Generate random Initialization Vector (IV)
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);

            cipher.init(Cipher.ENCRYPT_MODE, key, ivSpec);

            byte[] encryptedMessageBytes = cipher.doFinal(data);
            
            // Concat IV e Ciphertext
            byte[] ivAndCiphertext = new byte[IV_LENGTH + encryptedMessageBytes.length];
            System.arraycopy(iv, 0, ivAndCiphertext, 0, IV_LENGTH);
            System.arraycopy(encryptedMessageBytes, 0, ivAndCiphertext, IV_LENGTH, encryptedMessageBytes.length);

            return ivAndCiphertext;

        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException |
                 InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
            e.printStackTrace();
            return null;
        }
    }

    public String encrypt(String openText) {
        byte[] encryptedBytes = encrypt(openText.getBytes());
        if (encryptedBytes == null) return null;
        
        String encryptedMessage = Base64.getEncoder().encodeToString(encryptedBytes);
        System.out.println(">> Mensagem cifrada (com IV): " + encryptedMessage);
        return encryptedMessage;
    }

    public byte[] decrypt(byte[] ivAndCiphertext) {
        try {
            // Extract IV
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(ivAndCiphertext, 0, iv, 0, IV_LENGTH);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);

            // Extract Ciphertext
            int ciphertextLength = ivAndCiphertext.length - IV_LENGTH;
            byte[] ciphertext = new byte[ciphertextLength];
            System.arraycopy(ivAndCiphertext, IV_LENGTH, ciphertext, 0, ciphertextLength);

            Cipher decryptor = Cipher.getInstance(CIPHER_INSTANCE);
            decryptor.init(Cipher.DECRYPT_MODE, key, ivSpec);

            return decryptor.doFinal(ciphertext);

        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException |
                 InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
            e.printStackTrace();
            return null;
        }
    }

    public String decrypt(String encryptedTextBase64) {
        byte[] ivAndCiphertext = Base64.getDecoder().decode(encryptedTextBase64);
        byte[] decryptedBytes = decrypt(ivAndCiphertext);
        if (decryptedBytes == null) return null;
        
        return new String(decryptedBytes);
    }
}