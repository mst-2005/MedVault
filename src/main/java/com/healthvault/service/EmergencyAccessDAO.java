package com.healthvault.service;

import com.healthvault.config.DatabaseConfig;
import com.healthvault.crypto.PasswordService;
import com.healthvault.util.AuditLoggerUtil;
import com.healthvault.util.HealthIdUtil;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Emergency access service for critical medical situations
 */
public class EmergencyAccessDAO {
    
    /**
     * Generate emergency access code for a user
     */
    public String generateEmergencyAccess(int userId, String reason) throws Exception {
        // Check if user already has active emergency access
        if (hasActiveEmergencyAccess(userId)) {
            throw new Exception("UserModel already has active emergency access");
        }
        
        String emergencyCode = HealthIdUtil.generateEmergencyCode();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(24); // 24-hour validity
        
        String sql = "INSERT INTO emergency_access (user_id, emergency_code, access_reason, expires_at) " +
                    "VALUES (?, ?, ?, ?)";
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, userId);
            stmt.setString(2, emergencyCode);
            stmt.setString(3, reason);
            stmt.setTimestamp(4, Timestamp.valueOf(expiresAt));
            
            int rowsInserted = stmt.executeUpdate();
            
            if (rowsInserted > 0) {
                AuditLoggerUtil.logUserAction(userId, "EMERGENCY_ACCESS_GENERATED", "USER", userId,
                                        String.format("Emergency access generated: %s, Reason: %s", emergencyCode, reason));
                return emergencyCode;
            }
        }
        
        throw new SQLException("Failed to generate emergency access");
    }
    
    /**
     * Validate and use emergency access code
     */
    public EmergencyAccessInfo validateEmergencyAccess(String emergencyCode, String accessedBy) {
        String sql = "SELECT ea.*, u.name as patient_name, u.health_id as patient_health_id " +
                    "FROM emergency_access ea " +
                    "JOIN users u ON ea.user_id = u.id " +
                    "WHERE ea.emergency_code = ? AND ea.is_used = FALSE " +
                    "AND ea.expires_at > NOW()";
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, emergencyCode);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    EmergencyAccessInfo info = new EmergencyAccessInfo();
                    info.setUserId(rs.getInt("user_id"));
                    info.setPatientName(rs.getString("patient_name"));
                    info.setPatientHealthId(rs.getString("patient_health_id"));
                    info.setEmergencyCode(rs.getString("emergency_code"));
                    info.setAccessReason(rs.getString("access_reason"));
                    info.setExpiresAt(rs.getTimestamp("expires_at").toLocalDateTime());
                    info.setAccessedBy(accessedBy);
                    
                    // Mark as used
                    markEmergencyAccessAsUsed(rs.getInt("id"));
                    
                    // Log emergency access
                    AuditLoggerUtil.logEmergencyAccess(info.getUserId(), emergencyCode, accessedBy);
                    
                    return info;
                }
            }
        } catch (SQLException e) {
            AuditLoggerUtil.logSystemEvent("DATABASE_ERROR", "Emergency access validation failed: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Get emergency access history for a user
     */
    public List<EmergencyAccessInfo> getEmergencyAccessHistory(int userId) {
        List<EmergencyAccessInfo> history = new ArrayList<>();
        
        String sql = "SELECT ea.*, u.name as patient_name, u.health_id as patient_health_id " +
                    "FROM emergency_access ea " +
                    "JOIN users u ON ea.user_id = u.id " +
                    "WHERE ea.user_id = ? ORDER BY ea.created_at DESC";
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, userId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    EmergencyAccessInfo info = new EmergencyAccessInfo();
                    info.setUserId(rs.getInt("user_id"));
                    info.setPatientName(rs.getString("patient_name"));
                    info.setPatientHealthId(rs.getString("patient_health_id"));
                    info.setEmergencyCode(rs.getString("emergency_code"));
                    info.setAccessReason(rs.getString("access_reason"));
                    info.setAccessedBy(rs.getString("access_granted_to"));
                    info.setExpiresAt(rs.getTimestamp("expires_at") != null ? 
                                     rs.getTimestamp("expires_at").toLocalDateTime() : null);
                    info.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                    info.setUsed(rs.getBoolean("is_used"));
                    
                    history.add(info);
                }
            }
        } catch (SQLException e) {
            AuditLoggerUtil.logSystemEvent("DATABASE_ERROR", "Emergency access history failed: " + e.getMessage());
        }
        
        return history;
    }
    
    /**
     * Revoke emergency access
     */
    public boolean revokeEmergencyAccess(int userId, String emergencyCode) {
        String sql = "UPDATE emergency_access SET expires_at = NOW() " +
                    "WHERE user_id = ? AND emergency_code = ? AND is_used = FALSE";
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, userId);
            stmt.setString(2, emergencyCode);
            
            int rowsUpdated = stmt.executeUpdate();
            
            if (rowsUpdated > 0) {
                AuditLoggerUtil.logUserAction(userId, "EMERGENCY_ACCESS_REVOKED", "USER", userId,
                                        String.format("Emergency access revoked: %s", emergencyCode));
                return true;
            }
        } catch (SQLException e) {
            AuditLoggerUtil.logSystemEvent("DATABASE_ERROR", "Emergency access revocation failed: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Extend emergency access validity
     */
    public boolean extendEmergencyAccess(int userId, String emergencyCode, int additionalHours) {
        String sql = "UPDATE emergency_access SET expires_at = DATE_ADD(expires_at, INTERVAL ? HOUR) " +
                    "WHERE user_id = ? AND emergency_code = ? AND is_used = FALSE AND expires_at > NOW()";
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, additionalHours);
            stmt.setInt(2, userId);
            stmt.setString(3, emergencyCode);
            
            int rowsUpdated = stmt.executeUpdate();
            
            if (rowsUpdated > 0) {
                AuditLoggerUtil.logUserAction(userId, "EMERGENCY_ACCESS_EXTENDED", "USER", userId,
                                        String.format("Emergency access extended: %s, Hours: %d", emergencyCode, additionalHours));
                return true;
            }
        } catch (SQLException e) {
            AuditLoggerUtil.logSystemEvent("DATABASE_ERROR", "Emergency access extension failed: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Clean up expired emergency access codes
     */
    public int cleanupExpiredEmergencyAccess() {
        String sql = "UPDATE emergency_access SET expires_at = NOW() " +
                    "WHERE expires_at < NOW() AND is_used = FALSE";
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            int rowsUpdated = stmt.executeUpdate();
            
            if (rowsUpdated > 0) {
                AuditLoggerUtil.logSystemMaintenance("CLEANUP_EMERGENCY_ACCESS", 
                                              String.format("Cleaned up %d expired emergency access codes", rowsUpdated));
            }
            
            return rowsUpdated;
        } catch (SQLException e) {
            AuditLoggerUtil.logSystemEvent("DATABASE_ERROR", "Emergency access cleanup failed: " + e.getMessage());
        }
        
        return 0;
    }
    
    /**
     * Get active emergency access for user
     */
    public Optional<EmergencyAccessInfo> getActiveEmergencyAccess(int userId) {
        String sql = "SELECT ea.*, u.name as patient_name, u.health_id as patient_health_id " +
                    "FROM emergency_access ea " +
                    "JOIN users u ON ea.user_id = u.id " +
                    "WHERE ea.user_id = ? AND ea.is_used = FALSE AND ea.expires_at > NOW()";
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, userId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    EmergencyAccessInfo info = new EmergencyAccessInfo();
                    info.setUserId(rs.getInt("user_id"));
                    info.setPatientName(rs.getString("patient_name"));
                    info.setPatientHealthId(rs.getString("patient_health_id"));
                    info.setEmergencyCode(rs.getString("emergency_code"));
                    info.setAccessReason(rs.getString("access_reason"));
                    info.setExpiresAt(rs.getTimestamp("expires_at").toLocalDateTime());
                    info.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                    info.setUsed(false);
                    
                    return Optional.of(info);
                }
            }
        } catch (SQLException e) {
            AuditLoggerUtil.logSystemEvent("DATABASE_ERROR", "Get active emergency access failed: " + e.getMessage());
        }
        
        return Optional.empty();
    }
    
    /**
     * Generate temporary emergency password for medical staff
     */
    public String generateEmergencyPassword(int userId, String staffName, String hospitalName) {
        String tempPassword = PasswordService.generateTemporaryPassword();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(2); // 2-hour validity
        
        // In a real implementation, this would be stored in a separate emergency_credentials table
        // For now, we'll just log it
        
        AuditLoggerUtil.logEmergencyAccess(userId, "TEMP_PASSWORD_" + tempPassword, 
                                   String.format("Staff: %s, Hospital: %s", staffName, hospitalName));
        
        return tempPassword;
    }
    
    // Private helper methods
    
    private boolean hasActiveEmergencyAccess(int userId) {
        String sql = "SELECT COUNT(*) FROM emergency_access " +
                    "WHERE user_id = ? AND is_used = FALSE AND expires_at > NOW()";
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, userId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            AuditLoggerUtil.logSystemEvent("DATABASE_ERROR", "Active emergency access check failed: " + e.getMessage());
        }
        
        return false;
    }
    
    private void markEmergencyAccessAsUsed(int emergencyAccessId) {
        String sql = "UPDATE emergency_access SET is_used = TRUE, access_granted_to = CURRENT_TIMESTAMP " +
                    "WHERE id = ?";
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, emergencyAccessId);
            stmt.executeUpdate();
            
        } catch (SQLException e) {
            AuditLoggerUtil.logSystemEvent("DATABASE_ERROR", "Mark emergency access as used failed: " + e.getMessage());
        }
    }
    
    /**
     * Emergency access information class
     */
    public static class EmergencyAccessInfo {
        private int userId;
        private String patientName;
        private String patientHealthId;
        private String emergencyCode;
        private String accessReason;
        private String accessedBy;
        private LocalDateTime expiresAt;
        private LocalDateTime createdAt;
        private boolean used;
        
        // Getters and setters
        public int getUserId() { return userId; }
        public void setUserId(int userId) { this.userId = userId; }
        
        public String getPatientName() { return patientName; }
        public void setPatientName(String patientName) { this.patientName = patientName; }
        
        public String getPatientHealthId() { return patientHealthId; }
        public void setPatientHealthId(String patientHealthId) { this.patientHealthId = patientHealthId; }
        
        public String getEmergencyCode() { return emergencyCode; }
        public void setEmergencyCode(String emergencyCode) { this.emergencyCode = emergencyCode; }
        
        public String getAccessReason() { return accessReason; }
        public void setAccessReason(String accessReason) { this.accessReason = accessReason; }
        
        public String getAccessedBy() { return accessedBy; }
        public void setAccessedBy(String accessedBy) { this.accessedBy = accessedBy; }
        
        public LocalDateTime getExpiresAt() { return expiresAt; }
        public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
        
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        
        public boolean isUsed() { return used; }
        public void setUsed(boolean used) { this.used = used; }
        
        public boolean isExpired() {
            return expiresAt != null && expiresAt.isBefore(LocalDateTime.now());
        }
        
        public String getTimeRemaining() {
            if (expiresAt == null) {
                return "Unknown";
            }
            
            LocalDateTime now = LocalDateTime.now();
            if (expiresAt.isBefore(now)) {
                return "Expired";
            }
            
            long hours = java.time.Duration.between(now, expiresAt).toHours();
            if (hours > 24) {
                long days = hours / 24;
                return days + " day(s)";
            } else {
                return hours + " hour(s)";
            }
        }
        
        @Override
        public String toString() {
            return "EmergencyAccessInfo{" +
                    "userId=" + userId +
                    ", patientName='" + patientName + '\'' +
                    ", emergencyCode='" + emergencyCode + '\'' +
                    ", accessReason='" + accessReason + '\'' +
                    ", expiresAt=" + expiresAt +
                    ", used=" + used +
                    '}';
        }
    }
}
