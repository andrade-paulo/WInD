package com.wind.security;

import javax.crypto.Mac; 
import javax.crypto.spec.SecretKeySpec; 
import java.security.MessageDigest; 
import java.util.zip.CRC32; 
 
public class Util { 
    public static String bytes2Hex(byte[] bytes) { 
        StringBuilder sb = new StringBuilder(bytes.length * 2); 

        for (byte b : bytes) { 
            sb.append(String.format("%02x", b & 0xFF)); 
        } 

        return sb.toString(); 
    } 
 
    public static long calculateCRC32(byte[] messageBytes) { 
        CRC32 crc = new CRC32(); 
        crc.update(messageBytes); 

        return crc.getValue(); 
    } 
 
    public static byte[] calculateSHA256(byte[] data) throws Exception { 
        MessageDigest md = MessageDigest.getInstance("SHA-256"); 

        return md.digest(data); 
    } 
 
    public static byte[] calculateHmacSha256(byte[] key, byte[] messageBytes) throws Exception { 
        Mac mac = Mac.getInstance("HmacSHA256"); 
        SecretKeySpec keySpec = new SecretKeySpec(key, "HmacSHA256"); 
        mac.init(keySpec); 

        return mac.doFinal(messageBytes); 
    }
} 