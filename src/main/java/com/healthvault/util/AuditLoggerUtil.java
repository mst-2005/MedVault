package com.healthvault.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthvault.config.DatabaseConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Audit logging service for tracking all system activities
 * Essential for healthcare compliance and security monitoring
 */
public class AuditLoggerUtil {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Log user action
     */
    public static void logUserAction(int userId, String action, String targetType, int targetId, String details) {
        logAuditEvent(userId, action, targetType, targetId, null, null, details);
    }
    
    /**
     * Log user action with IP and user agent
     */
    public static void logUserAction(int userId, String action, String targetType, int targetId, 
                                   String ipAddress, String userAgent, String details) {
        logAuditEvent(userId, action, targetType, targetId, ipAddress, userAgent, details);
    }
    
    /**
     * Log system event
     */
    public static void logSystemEvent(String action) {
        logAuditEvent(null, action, null, null, null, null, null);
    }
    
    /**
     * Log system event with details
     */
    public static void logSystemEvent(String action, String details) {
        logAuditEvent(null, action, null, null, null, null, details);
    }
    
    /**
     * Log file access
     */
    public static void logFileAccess(int userId, int fileId, String action, String ipAddress) {
        String details = String.format("File %s accessed by user %d", action, userId);
        logAuditEvent(userId, action, "MEDICAL_FILE", fileId, ipAddress, null, details);
    }
    
    /**
     * Log file sharing
     */
    public static void logFileSharing(int ownerId, int fileId, String sharedWith, String action) {
        String details = String.format("File %d %s with %s", fileId, action, sharedWith);
        logAuditEvent(ownerId, "FILE_SHARING_" + action.toUpperCase(), "MEDICAL_FILE", fileId, null, null, details);
    }
    
    /**
     * Log authentication event
     */
    public static void logAuthenticationEvent(String email, String action, String ipAddress, boolean success) {
        String details = String.format("Authentication %s for email: %s", success ? "success" : "failed", email);
        logAuditEvent(null, "AUTH_" + action.toUpperCase(), null, null, ipAddress, null, details);
    }
    
    /**
     * Log data modification
     */
    public static void logDataModification(int userId, String dataType, int dataId, String action) {
        String details = String.format("%s %s with ID %d", action, dataType, dataId);
        logAuditEvent(userId, "DATA_MODIFICATION", dataType, dataId, null, null, details);
    }
    
    /**
     * Log security event
     */
    public static void logSecurityEvent(String eventType, String details, String ipAddress) {
        logAuditEvent(null, "SECURITY_" + eventType.toUpperCase(), null, null, ipAddress, null, details);
    }
    
    /**
     * Main audit logging method
     */
    private static void logAuditEvent(Integer userId, String action, String targetType, Integer targetId, 
                                    String ipAddress, String userAgent, String details) {
        String sql = "INSERT INTO audit_logs (user_id, action, target_type, target_id, ip_address, user_agent, details) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)";
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            if (userId != null) {
                stmt.setInt(1, userId);
            } else {
                stmt.setNull(1, java.sql.Types.INTEGER);
            }
            
            stmt.setString(2, action);
            stmt.setString(3, targetType);
            
            if (targetId != null) {
                stmt.setInt(4, targetId);
            } else {
                stmt.setNull(4, java.sql.Types.INTEGER);
            }
            
            stmt.setString(5, ipAddress);
            stmt.setString(6, userAgent);
            
            // Convert details to JSON if it's a complex object
            String detailsJson = details;
            if (details != null && details.startsWith("{")) {
                // Already JSON format
                detailsJson = details;
            } else if (details != null) {
                // Convert to JSON
                Map<String, Object> detailsMap = new HashMap<>();
                detailsMap.put("message", details);
                detailsMap.put("timestamp", LocalDateTime.now().toString());
                detailsJson = objectMapper.writeValueAsString(detailsMap);
            }
            
            stmt.setString(7, detailsJson);
            stmt.executeUpdate();
            
        } catch (SQLException e) {
            throw new RuntimeException("Audit Logging Failed: " + e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException("Audit Logging System Error: " + e.getMessage());
        }
    }
    
    /**
     * Get recent audit logs for a user
     */
    public static void logUserActivitySummary(int userId) {
        String details = String.format("Activity summary generated for user %d", userId);
        logAuditEvent(userId, "ACTIVITY_SUMMARY", "USER", userId, null, null, details);
    }
    
    /**
     * Log emergency access
     */
    public static void logEmergencyAccess(int userId, String emergencyCode, String accessedBy) {
        String details = String.format("Emergency access granted using code %s by %s", emergencyCode, accessedBy);
        logAuditEvent(userId, "EMERGENCY_ACCESS", "USER", userId, null, null, details);
    }
    
    /**
     * Log configuration change
     */
    public static void logConfigurationChange(int userId, String configType, String oldValue, String newValue) {
        String details = String.format("Configuration %s changed from '%s' to '%s'", configType, oldValue, newValue);
        logAuditEvent(userId, "CONFIG_CHANGE", "CONFIGURATION", 0, null, null, details);
    }
    
    /**
     * Log data export
     */
    public static void logDataExport(int userId, String dataType, int recordCount) {
        String details = String.format("Exported %d records of type %s", recordCount, dataType);
        logAuditEvent(userId, "DATA_EXPORT", dataType, 0, null, null, details);
    }
    
    /**
     * Log data import
     */
    public static void logDataImport(int userId, String dataType, int recordCount) {
        String details = String.format("Imported %d records of type %s", recordCount, dataType);
        logAuditEvent(userId, "DATA_IMPORT", dataType, 0, null, null, details);
    }
    
    /**
     * Log backup operation
     */
    public static void logBackupOperation(int userId, String operation, boolean success) {
        String details = String.format("Backup %s %s", operation, success ? "successful" : "failed");
        logAuditEvent(userId, "BACKUP_" + operation.toUpperCase(), "SYSTEM", 0, null, null, details);
    }
    
    /**
     * Log system maintenance
     */
    public static void logSystemMaintenance(String operation, String details) {
        logAuditEvent(null, "SYSTEM_MAINTENANCE_" + operation.toUpperCase(), "SYSTEM", 0, null, null, details);
    }
}
