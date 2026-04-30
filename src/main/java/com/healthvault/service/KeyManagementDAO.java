package com.healthvault.service;

import com.healthvault.config.DatabaseConfig;
import com.healthvault.crypto.EncryptionService;

import javax.crypto.SecretKey;
import java.sql.*;
import java.util.Base64;

/**
 * Key management service for handling user encryption keys
 */
public class KeyManagementDAO {
    
    /**
     * Generate and store encryption key for a user
     */
    public String generateUserKey(int userId, String masterPassword) throws Exception {
        // Generate new encryption key
        String keyString = EncryptionService.generateKey();
        // Use a consistent salt for master key derivation so it can be reproduced
        byte[] salt = "HealthVault_Salt".getBytes(); 
        SecretKey userKey = EncryptionService.deriveKey(masterPassword, salt);
        
        // Encrypt the key with master password
        String encryptedKey = EncryptionService.encryptString(keyString, userKey);
        
        // Store encrypted key in database
        String sql = "INSERT INTO encryption_keys (user_id, key_name, encrypted_key, key_algorithm, key_size) " +
                    "VALUES (?, ?, ?, ?, ?)";
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, userId);
            stmt.setString(2, "USER_MASTER_KEY");
            stmt.setString(3, encryptedKey);
            stmt.setString(4, "AES");
            stmt.setInt(5, 256);
            
            int rowsInserted = stmt.executeUpdate();
            if (rowsInserted > 0) {
                return keyString;
            }
        }
        
        throw new SQLException("Failed to store encryption key");
    }
    
    /**
     * Retrieve and decrypt user's encryption key
     */
    public SecretKey getUserKey(int userId, String masterPassword) throws Exception {
        String sql = "SELECT encrypted_key FROM encryption_keys WHERE user_id = ? AND key_name = ?";
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, userId);
            stmt.setString(2, "USER_MASTER_KEY");
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String encryptedKey = rs.getString("encrypted_key");
                    
                    // Derive key from master password using the same consistent salt
                    byte[] salt = "HealthVault_Salt".getBytes();
                    SecretKey masterKey = EncryptionService.deriveKey(masterPassword, salt);
                    
                    // Decrypt user key
                    String decryptedKeyString = EncryptionService.decryptString(encryptedKey, masterKey);
                    
                    // Convert to SecretKey
                    byte[] keyBytes = Base64.getDecoder().decode(decryptedKeyString);
                    return new javax.crypto.spec.SecretKeySpec(keyBytes, "AES");
                }
            }
        }
        
        throw new SQLException("UserModel encryption key not found");
    }
    
    /**
     * Check if user has encryption key
     */
    public boolean hasUserKey(int userId) {
        String sql = "SELECT COUNT(*) FROM encryption_keys WHERE user_id = ? AND key_name = ?";
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, userId);
            stmt.setString(2, "USER_MASTER_KEY");
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            // Log error but return false
        }
        
        return false;
    }
    
    /**
     * Rotate user encryption key
     */
    public boolean rotateUserKey(int userId, String oldMasterPassword, String newMasterPassword) {
        try {
            // Get current encrypted key
            String sql = "SELECT encrypted_key FROM encryption_keys WHERE user_id = ? AND key_name = ?";
            
            try (Connection conn = DatabaseConfig.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setInt(1, userId);
                stmt.setString(2, "USER_MASTER_KEY");
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String encryptedKey = rs.getString("encrypted_key");
                        
                        // Decrypt with old master password
                        byte[] salt = "HealthVault_Salt".getBytes();
                        SecretKey oldMasterKey = EncryptionService.deriveKey(oldMasterPassword, salt);
                        String keyString = EncryptionService.decryptString(encryptedKey, oldMasterKey);
                        
                        // Re-encrypt with new master password
                        SecretKey newMasterKey = EncryptionService.deriveKey(newMasterPassword, salt);
                        String newEncryptedKey = EncryptionService.encryptString(keyString, newMasterKey);
                        
                        // Update database
                        String updateSql = "UPDATE encryption_keys SET encrypted_key = ? WHERE user_id = ? AND key_name = ?";
                        
                        try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                            updateStmt.setString(1, newEncryptedKey);
                            updateStmt.setInt(2, userId);
                            updateStmt.setString(3, "USER_MASTER_KEY");
                            
                            return updateStmt.executeUpdate() > 0;
                        }
                    }
                }
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    /**
     * Delete user's encryption key (used for resets/fixes)
     */
    public boolean deleteUserKey(int userId) {
        String sql = "DELETE FROM encryption_keys WHERE user_id = ? AND key_name = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setString(2, "USER_MASTER_KEY");
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }
}
