package com.healthvault.service;

import com.healthvault.config.DatabaseConfig;
import com.healthvault.crypto.EncryptionService;
import com.healthvault.model.UserModel;
import com.healthvault.util.AuditLoggerUtil;
import com.healthvault.util.HealthIdUtil;

import javax.crypto.SecretKey;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Secure file sharing service with temporary access tokens and encrypted links
 */
public class SharingDAO {
    
    /**
     * Share file with another user
     */
    public boolean shareFileWithUser(int ownerId, int fileId, int sharedWithUserId, 
                                   AccessType accessType, LocalDateTime expiresAt) {
        
        // Verify ownership
        if (!verifyFileOwnership(ownerId, fileId)) {
            AuditLoggerUtil.logSecurityEvent("UNAUTHORIZED_SHARING_ATTEMPT", 
                                      String.format("UserModel %d attempted to share file %d", ownerId, fileId), 
                                      null);
            return false;
        }
        
        // Check if sharing already exists
        if (sharingExists(fileId, sharedWithUserId)) {
            return updateExistingSharing(fileId, sharedWithUserId, accessType, expiresAt);
        }
        
        // Create new sharing record
        String sql = "INSERT INTO access_control (file_id, owner_id, shared_with_user_id, " +
                    "access_type, expires_at, is_active) VALUES (?, ?, ?, ?, ?, TRUE)";
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, fileId);
            stmt.setInt(2, ownerId);
            stmt.setInt(3, sharedWithUserId);
            stmt.setString(4, accessType.name());
            stmt.setTimestamp(5, expiresAt != null ? Timestamp.valueOf(expiresAt) : null);
            
            int rowsInserted = stmt.executeUpdate();
            
            if (rowsInserted > 0) {
                AuditLoggerUtil.logFileSharing(ownerId, fileId, "UserModel ID: " + sharedWithUserId, "SHARED");
                return true;
            }
        } catch (SQLException e) {
            AuditLoggerUtil.logSystemEvent("DATABASE_ERROR", "File sharing failed: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Create temporary sharing link with access token
     */
    public String createTemporaryShareLink(int ownerId, int fileId, AccessType accessType, 
                                        int hoursValid) {
        
        // Verify ownership
        if (!verifyFileOwnership(ownerId, fileId)) {
            AuditLoggerUtil.logSecurityEvent("UNAUTHORIZED_LINK_CREATION", 
                                      String.format("UserModel %d attempted to create link for file %d", ownerId, fileId), 
                                      null);
            return null;
        }
        
        // Generate unique access token
        String accessToken = HealthIdUtil.generateAccessToken();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(hoursValid);
        
        // Create sharing record with token
        String sql = "INSERT INTO access_control (file_id, owner_id, access_token, " +
                    "access_type, expires_at, is_active) VALUES (?, ?, ?, ?, ?, TRUE)";
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, fileId);
            stmt.setInt(2, ownerId);
            stmt.setString(3, accessToken);
            stmt.setString(4, accessType.name());
            stmt.setTimestamp(5, Timestamp.valueOf(expiresAt));
            
            int rowsInserted = stmt.executeUpdate();
            
            if (rowsInserted > 0) {
                AuditLoggerUtil.logFileSharing(ownerId, fileId, "Temporary link", "SHARED");
                return generateShareUrl(accessToken);
            }
        } catch (SQLException e) {
            AuditLoggerUtil.logSystemEvent("DATABASE_ERROR", "Temporary link creation failed: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Validate and retrieve file via access token
     */
    public Optional<SharedFileInfo> validateAccessToken(String accessToken) {
        String sql = "SELECT ac.*, mf.original_file_name, mf.file_type, mf.user_id as file_owner_id " +
                    "FROM access_control ac " +
                    "JOIN medical_files mf ON ac.file_id = mf.id " +
                    "WHERE ac.access_token = ? AND ac.is_active = TRUE " +
                    "AND (ac.expires_at IS NULL OR ac.expires_at > NOW())";
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, accessToken);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    SharedFileInfo info = new SharedFileInfo();
                    info.setFileId(rs.getInt("file_id"));
                    info.setOwnerId(rs.getInt("owner_id"));
                    info.setAccessType(AccessType.valueOf(rs.getString("access_type")));
                    info.setExpiresAt(rs.getTimestamp("expires_at") != null ? 
                                    rs.getTimestamp("expires_at").toLocalDateTime() : null);
                    info.setFileName(rs.getString("original_file_name"));
                    info.setFileType(rs.getString("file_type"));
                    info.setFileOwnerId(rs.getInt("file_owner_id"));
                    
                    // Log access
                    AuditLoggerUtil.logFileAccess(0, info.getFileId(), "ACCESSED_VIA_TOKEN", null);
                    
                    return Optional.of(info);
                }
            }
        } catch (SQLException e) {
            AuditLoggerUtil.logSystemEvent("DATABASE_ERROR", "Access token validation failed: " + e.getMessage());
        }
        
        return Optional.empty();
    }
    
    /**
     * Get files shared with user
     */
    public List<SharedFileInfo> getFilesSharedWithUser(int userId) {
        List<SharedFileInfo> sharedFiles = new ArrayList<>();
        
        String sql = "SELECT ac.*, mf.original_file_name, mf.file_type, mf.upload_date, " +
                    "u.name as owner_name, u.health_id as owner_health_id " +
                    "FROM access_control ac " +
                    "JOIN medical_files mf ON ac.file_id = mf.id " +
                    "JOIN users u ON ac.owner_id = u.id " +
                    "WHERE ac.shared_with_user_id = ? AND ac.is_active = TRUE " +
                    "AND (ac.expires_at IS NULL OR ac.expires_at > NOW()) " +
                    "ORDER BY ac.created_at DESC";
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, userId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    SharedFileInfo info = new SharedFileInfo();
                    info.setFileId(rs.getInt("file_id"));
                    info.setOwnerId(rs.getInt("owner_id"));
                    info.setAccessType(AccessType.valueOf(rs.getString("access_type")));
                    info.setExpiresAt(rs.getTimestamp("expires_at") != null ? 
                                    rs.getTimestamp("expires_at").toLocalDateTime() : null);
                    info.setFileName(rs.getString("original_file_name"));
                    info.setFileType(rs.getString("file_type"));
                    info.setUploadDate(rs.getDate("upload_date") != null ? 
                                      rs.getDate("upload_date").toLocalDate() : null);
                    info.setOwnerName(rs.getString("owner_name"));
                    info.setOwnerHealthId(rs.getString("owner_health_id"));
                    
                    sharedFiles.add(info);
                }
            }
        } catch (SQLException e) {
            AuditLoggerUtil.logSystemEvent("DATABASE_ERROR", "Get shared files failed: " + e.getMessage());
        }
        
        return sharedFiles;
    }
    
    /**
     * Get files shared by user
     */
    public List<SharedFileInfo> getFilesSharedByUser(int userId) {
        List<SharedFileInfo> sharedFiles = new ArrayList<>();
        
        String sql = "SELECT ac.*, mf.original_file_name, mf.file_type, mf.upload_date, " +
                    "shared_user.name as shared_with_name, shared_user.health_id as shared_with_health_id " +
                    "FROM access_control ac " +
                    "JOIN medical_files mf ON ac.file_id = mf.id " +
                    "LEFT JOIN users shared_user ON ac.shared_with_user_id = shared_user.id " +
                    "WHERE ac.owner_id = ? AND ac.is_active = TRUE " +
                    "AND (ac.expires_at IS NULL OR ac.expires_at > NOW()) " +
                    "ORDER BY ac.created_at DESC";
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, userId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    SharedFileInfo info = new SharedFileInfo();
                    info.setFileId(rs.getInt("file_id"));
                    info.setOwnerId(rs.getInt("owner_id"));
                    info.setAccessType(AccessType.valueOf(rs.getString("access_type")));
                    info.setExpiresAt(rs.getTimestamp("expires_at") != null ? 
                                    rs.getTimestamp("expires_at").toLocalDateTime() : null);
                    info.setFileName(rs.getString("original_file_name"));
                    info.setFileType(rs.getString("file_type"));
                    info.setUploadDate(rs.getDate("upload_date") != null ? 
                                      rs.getDate("upload_date").toLocalDate() : null);
                    info.setSharedWithName(rs.getString("shared_with_name"));
                    info.setSharedWithHealthId(rs.getString("shared_with_health_id"));
                    info.setHasAccessToken(rs.getString("access_token") != null);
                    
                    sharedFiles.add(info);
                }
            }
        } catch (SQLException e) {
            AuditLoggerUtil.logSystemEvent("DATABASE_ERROR", "Get shared by user failed: " + e.getMessage());
        }
        
        return sharedFiles;
    }
    
    /**
     * Revoke file sharing
     */
    public boolean revokeSharing(int ownerId, int fileId, int sharedWithUserId) {
        String sql = "UPDATE access_control SET is_active = FALSE " +
                    "WHERE file_id = ? AND owner_id = ? AND shared_with_user_id = ?";
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, fileId);
            stmt.setInt(2, ownerId);
            stmt.setInt(3, sharedWithUserId);
            
            int rowsUpdated = stmt.executeUpdate();
            
            if (rowsUpdated > 0) {
                AuditLoggerUtil.logFileSharing(ownerId, fileId, "UserModel ID: " + sharedWithUserId, "REVOKED");
                return true;
            }
        } catch (SQLException e) {
            AuditLoggerUtil.logSystemEvent("DATABASE_ERROR", "Revoke sharing failed: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Revoke temporary access token
     */
    public boolean revokeAccessToken(int ownerId, String accessToken) {
        String sql = "UPDATE access_control SET is_active = FALSE " +
                    "WHERE access_token = ? AND owner_id = ?";
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, accessToken);
            stmt.setInt(2, ownerId);
            
            int rowsUpdated = stmt.executeUpdate();
            
            if (rowsUpdated > 0) {
                AuditLoggerUtil.logFileSharing(ownerId, 0, "Token: " + accessToken, "REVOKED");
                return true;
            }
        } catch (SQLException e) {
            AuditLoggerUtil.logSystemEvent("DATABASE_ERROR", "Revoke token failed: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Extend sharing expiration
     */
    public boolean extendSharing(int ownerId, int fileId, int sharedWithUserId, LocalDateTime newExpiresAt) {
        String sql = "UPDATE access_control SET expires_at = ? " +
                    "WHERE file_id = ? AND owner_id = ? AND shared_with_user_id = ? AND is_active = TRUE";
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setTimestamp(1, Timestamp.valueOf(newExpiresAt));
            stmt.setInt(2, fileId);
            stmt.setInt(3, ownerId);
            stmt.setInt(4, sharedWithUserId);
            
            int rowsUpdated = stmt.executeUpdate();
            
            if (rowsUpdated > 0) {
                AuditLoggerUtil.logFileSharing(ownerId, fileId, "UserModel ID: " + sharedWithUserId, "EXTENDED");
                return true;
            }
        } catch (SQLException e) {
            AuditLoggerUtil.logSystemEvent("DATABASE_ERROR", "Extend sharing failed: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Check if user has access to file
     */
    public boolean hasFileAccess(int userId, int fileId) {
        String sql = "SELECT COUNT(*) FROM access_control ac " +
                    "WHERE ac.file_id = ? AND ac.shared_with_user_id = ? " +
                    "AND ac.is_active = TRUE AND (ac.expires_at IS NULL OR ac.expires_at > NOW())";
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, fileId);
            stmt.setInt(2, userId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            AuditLoggerUtil.logSystemEvent("DATABASE_ERROR", "File access check failed: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Clean up expired sharing links
     */
    public int cleanupExpiredSharing() {
        String sql = "UPDATE access_control SET is_active = FALSE " +
                    "WHERE expires_at IS NOT NULL AND expires_at < NOW() AND is_active = TRUE";
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            int rowsUpdated = stmt.executeUpdate();
            
            if (rowsUpdated > 0) {
                AuditLoggerUtil.logSystemMaintenance("CLEANUP_EXPIRED_SHARING", 
                                              String.format("Cleaned up %d expired sharing links", rowsUpdated));
            }
            
            return rowsUpdated;
        } catch (SQLException e) {
            AuditLoggerUtil.logSystemEvent("DATABASE_ERROR", "Cleanup expired sharing failed: " + e.getMessage());
        }
        
        return 0;
    }
    
    // Private helper methods
    
    private boolean verifyFileOwnership(int userId, int fileId) {
        String sql = "SELECT COUNT(*) FROM medical_files WHERE id = ? AND user_id = ?";
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, fileId);
            stmt.setInt(2, userId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            AuditLoggerUtil.logSystemEvent("DATABASE_ERROR", "File ownership check failed: " + e.getMessage());
        }
        
        return false;
    }
    
    private boolean sharingExists(int fileId, int sharedWithUserId) {
        String sql = "SELECT COUNT(*) FROM access_control " +
                    "WHERE file_id = ? AND shared_with_user_id = ? AND is_active = TRUE";
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, fileId);
            stmt.setInt(2, sharedWithUserId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            AuditLoggerUtil.logSystemEvent("DATABASE_ERROR", "Sharing existence check failed: " + e.getMessage());
        }
        
        return false;
    }
    
    private boolean updateExistingSharing(int fileId, int sharedWithUserId, 
                                      AccessType accessType, LocalDateTime expiresAt) {
        String sql = "UPDATE access_control SET access_type = ?, expires_at = ? " +
                    "WHERE file_id = ? AND shared_with_user_id = ? AND is_active = TRUE";
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, accessType.name());
            stmt.setTimestamp(2, expiresAt != null ? Timestamp.valueOf(expiresAt) : null);
            stmt.setInt(3, fileId);
            stmt.setInt(4, sharedWithUserId);
            
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            AuditLoggerUtil.logSystemEvent("DATABASE_ERROR", "Update existing sharing failed: " + e.getMessage());
        }
        
        return false;
    }
    
    private String generateShareUrl(String accessToken) {
        // In a real application, this would be the actual application URL
        return "https://healthvault.com/shared/" + accessToken;
    }
    
    /**
     * Access type enum
     */
    public enum AccessType {
        READ, READ_WRITE
    }
    
    /**
     * Shared file information class
     */
    public static class SharedFileInfo {
        private int fileId;
        private int ownerId;
        private AccessType accessType;
        private LocalDateTime expiresAt;
        private String fileName;
        private String fileType;
        private java.time.LocalDate uploadDate;
        private String ownerName;
        private String ownerHealthId;
        private String sharedWithName;
        private String sharedWithHealthId;
        private int fileOwnerId;
        private boolean hasAccessToken;
        
        // Getters and setters
        public int getFileId() { return fileId; }
        public void setFileId(int fileId) { this.fileId = fileId; }
        
        public int getOwnerId() { return ownerId; }
        public void setOwnerId(int ownerId) { this.ownerId = ownerId; }
        
        public AccessType getAccessType() { return accessType; }
        public void setAccessType(AccessType accessType) { this.accessType = accessType; }
        
        public LocalDateTime getExpiresAt() { return expiresAt; }
        public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
        
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        
        public String getFileType() { return fileType; }
        public void setFileType(String fileType) { this.fileType = fileType; }
        
        public java.time.LocalDate getUploadDate() { return uploadDate; }
        public void setUploadDate(java.time.LocalDate uploadDate) { this.uploadDate = uploadDate; }
        
        public String getOwnerName() { return ownerName; }
        public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
        
        public String getOwnerHealthId() { return ownerHealthId; }
        public void setOwnerHealthId(String ownerHealthId) { this.ownerHealthId = ownerHealthId; }
        
        public String getSharedWithName() { return sharedWithName; }
        public void setSharedWithName(String sharedWithName) { this.sharedWithName = sharedWithName; }
        
        public String getSharedWithHealthId() { return sharedWithHealthId; }
        public void setSharedWithHealthId(String sharedWithHealthId) { this.sharedWithHealthId = sharedWithHealthId; }
        
        public int getFileOwnerId() { return fileOwnerId; }
        public void setFileOwnerId(int fileOwnerId) { this.fileOwnerId = fileOwnerId; }
        
        public boolean isHasAccessToken() { return hasAccessToken; }
        public void setHasAccessToken(boolean hasAccessToken) { this.hasAccessToken = hasAccessToken; }
        
        public boolean isExpired() {
            return expiresAt != null && expiresAt.isBefore(LocalDateTime.now());
        }
        
        public String getAccessLevelDisplay() {
            return accessType == AccessType.READ ? "Read Only" : "Read/Write";
        }
    }
}
