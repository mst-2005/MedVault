package com.healthvault.crypto;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Base64;

/**
 * Advanced Encryption Standard (AES) service for securing medical data
 * Implements AES-256 with PBKDF2 key derivation and secure IV generation
 */
public class EncryptionService {
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";
    private static final int KEY_LENGTH = 256;
    private static final int IV_LENGTH = 16;
    private static final int SALT_LENGTH = 16;
    private static final int ITERATION_COUNT = 65536;
    
    private static final SecureRandom secureRandom = new SecureRandom();
    
    /**
     * Generate a random encryption key
     */
    public static String generateKey() throws NoSuchAlgorithmException {
        KeyGenerator keyGenerator = KeyGenerator.getInstance(ALGORITHM);
        keyGenerator.init(KEY_LENGTH);
        SecretKey secretKey = keyGenerator.generateKey();
        return Base64.getEncoder().encodeToString(secretKey.getEncoded());
    }
    
    /**
     * Derive key from password using PBKDF2
     */
    public static SecretKey deriveKey(String password, byte[] salt) 
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATION_COUNT, KEY_LENGTH);
        return new SecretKeySpec(factory.generateSecret(spec).getEncoded(), ALGORITHM);
    }
    
    /**
     * Generate random salt
     */
    public static byte[] generateSalt() {
        byte[] salt = new byte[SALT_LENGTH];
        secureRandom.nextBytes(salt);
        return salt;
    }
    
    /**
     * Generate random IV
     */
    public static byte[] generateIV() {
        byte[] iv = new byte[IV_LENGTH];
        secureRandom.nextBytes(iv);
        return iv;
    }
    
    /**
     * Encrypt file data
     */
    public static EncryptedData encryptFile(byte[] fileData, SecretKey secretKey) 
            throws NoSuchPaddingException, NoSuchAlgorithmException, 
                   InvalidAlgorithmParameterException, InvalidKeyException, 
                   BadPaddingException, IllegalBlockSizeException {
        
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        byte[] iv = generateIV();
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);
        
        byte[] encryptedData = cipher.doFinal(fileData);
        
        return new EncryptedData(encryptedData, iv);
    }
    
    /**
     * Decrypt file data
     */
    public static byte[] decryptFile(EncryptedData encryptedData, SecretKey secretKey) 
            throws NoSuchPaddingException, NoSuchAlgorithmException, 
                   InvalidAlgorithmParameterException, InvalidKeyException, 
                   BadPaddingException, IllegalBlockSizeException {
        
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        IvParameterSpec ivSpec = new IvParameterSpec(encryptedData.getIv());
        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec);
        
        return cipher.doFinal(encryptedData.getData());
    }
    
    /**
     * Encrypt string data
     */
    public static String encryptString(String plainText, SecretKey secretKey) 
            throws NoSuchPaddingException, NoSuchAlgorithmException, 
                   InvalidAlgorithmParameterException, InvalidKeyException, 
                   BadPaddingException, IllegalBlockSizeException {
        
        byte[] plainData = plainText.getBytes(StandardCharsets.UTF_8);
        EncryptedData encrypted = encryptFile(plainData, secretKey);
        
        // Combine IV and encrypted data
        byte[] combined = new byte[IV_LENGTH + encrypted.getData().length];
        System.arraycopy(encrypted.getIv(), 0, combined, 0, IV_LENGTH);
        System.arraycopy(encrypted.getData(), 0, combined, IV_LENGTH, encrypted.getData().length);
        
        return Base64.getEncoder().encodeToString(combined);
    }
    
    /**
     * Decrypt string data
     */
    public static String decryptString(String encryptedText, SecretKey secretKey) 
            throws NoSuchPaddingException, NoSuchAlgorithmException, 
                   InvalidAlgorithmParameterException, InvalidKeyException, 
                   BadPaddingException, IllegalBlockSizeException {
        
        byte[] combined = Base64.getDecoder().decode(encryptedText);
        
        // Extract IV and encrypted data
        byte[] iv = new byte[IV_LENGTH];
        byte[] encryptedData = new byte[combined.length - IV_LENGTH];
        System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
        System.arraycopy(combined, IV_LENGTH, encryptedData, 0, encryptedData.length);
        
        EncryptedData encrypted = new EncryptedData(encryptedData, iv);
        byte[] decryptedData = decryptFile(encrypted, secretKey);
        
        return new String(decryptedData, StandardCharsets.UTF_8);
    }
    
    /**
     * Encrypt and save file to disk
     */
    public static void encryptAndSaveFile(File inputFile, File outputFile, SecretKey secretKey) 
            throws IOException, NoSuchPaddingException, NoSuchAlgorithmException, 
                   InvalidAlgorithmParameterException, InvalidKeyException, 
                   BadPaddingException, IllegalBlockSizeException {
        
        byte[] fileData = readFile(inputFile);
        EncryptedData encrypted = encryptFile(fileData, secretKey);
        
        // Save IV + encrypted data
        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            fos.write(encrypted.getIv());
            fos.write(encrypted.getData());
        }
    }
    
    /**
     * Load and decrypt file from disk
     */
    public static byte[] loadAndDecryptFile(File encryptedFile, SecretKey secretKey) 
            throws IOException, NoSuchPaddingException, NoSuchAlgorithmException, 
                   InvalidAlgorithmParameterException, InvalidKeyException, 
                   BadPaddingException, IllegalBlockSizeException {
        
        byte[] fileData = readFile(encryptedFile);
        
        // Extract IV and encrypted data
        byte[] iv = new byte[IV_LENGTH];
        byte[] encryptedData = new byte[fileData.length - IV_LENGTH];
        System.arraycopy(fileData, 0, iv, 0, IV_LENGTH);
        System.arraycopy(fileData, IV_LENGTH, encryptedData, 0, encryptedData.length);
        
        EncryptedData encrypted = new EncryptedData(encryptedData, iv);
        return decryptFile(encrypted, secretKey);
    }
    
    /**
     * Read file into byte array
     */
    private static byte[] readFile(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }
            return baos.toByteArray();
        }
    }
    
    /**
     * Generate SHA-256 hash of data
     */
    public static String generateHash(byte[] data) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data);
        return Base64.getEncoder().encodeToString(hash);
    }
    
    /**
     * Verify file integrity using hash
     */
    public static boolean verifyFileIntegrity(File file, String expectedHash) 
            throws IOException, NoSuchAlgorithmException {
        byte[] fileData = readFile(file);
        String actualHash = generateHash(fileData);
        return actualHash.equals(expectedHash);
    }
    
    /**
     * Container class for encrypted data with IV
     */
    public static class EncryptedData {
        private final byte[] data;
        private final byte[] iv;
        
        public EncryptedData(byte[] data, byte[] iv) {
            this.data = data;
            this.iv = iv;
        }
        
        public byte[] getData() {
            return data;
        }
        
        public byte[] getIv() {
            return iv;
        }
    }
}
