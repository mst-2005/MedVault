package com.healthvault.service;

import com.healthvault.config.DatabaseConfig;
import com.healthvault.model.UserModel;
import com.healthvault.util.AuditLoggerUtil;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Role-based access control service for managing permissions
 */
public class AccessControlDAO {
    
    /**
     * Check if user has permission for specific action
     */
    public boolean hasPermission(int userId, Permission permission, ResourceType resourceType, int resourceId) {
        // Check user type and role-based permissions
        Optional<UserModel> userOpt = UserDAO.getInstance().getUserById(userId);
        if (!userOpt.isPresent()) {
            return false;
        }
        
        UserModel user = userOpt.get();
        
        // Check basic role permissions
        if (!hasRolePermission(user.getUserType(), permission, resourceType)) {
            return false;
        }
        
        // Check resource-specific permissions
        switch (resourceType) {
            case MEDICAL_FILE:
                return hasFilePermission(userId, permission, resourceId);
            case USER_PROFILE:
                return hasProfilePermission(userId, permission, resourceId);
            case SYSTEM_CONFIG:
                return hasSystemPermission(user.getUserType(), permission);
            default:
                return false;
        }
    }
    
    /**
     * Grant permission to user
     */
    public boolean grantPermission(int granterId, int userId, Permission permission, 
                                 ResourceType resourceType, int resourceId, LocalDateTime expiresAt) {
        
        // Check if granter has permission to grant
        if (!hasPermission(granterId, Permission.GRANT_PERMISSION, resourceType, resourceId)) {
            AuditLoggerUtil.logSecurityEvent("UNAUTHORIZED_PERMISSION_GRANT", 
                                      String.format("UserModel %d attempted to grant permission to user %d", granterId, userId), 
                                      null);
            return false;
        }
        
        // Check if permission already exists
        if (permissionExists(userId, permission, resourceType, resourceId)) {
            return updateExistingPermission(userId, permission, resourceType, resourceId, expiresAt);
        }
        
        // Create new permission
        String sql = "INSERT INTO user_permissions (user_id, permission, resource_type, resource_id, " +
                    "granted_by, expires_at, is_active) VALUES (?, ?, ?, ?, ?, ?, TRUE)";
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, userId);
            stmt.setString(2, permission.name());
            stmt.setString(3, resourceType.name());
            stmt.setInt(4, resourceId);
            stmt.setInt(5, granterId);
            stmt.setTimestamp(6, expiresAt != null ? Timestamp.valueOf(expiresAt) : null);
            
            int rowsInserted = stmt.executeUpdate();
            
            if (rowsInserted > 0) {
                AuditLoggerUtil.logUserAction(granterId, "PERMISSION_GRANTED", resourceType.name(), resourceId,
                                        String.format("Granted %s permission to user %d", permission.name(), userId));
                return true;
            }
        } catch (SQLException e) {
            AuditLoggerUtil.logSystemEvent("DATABASE_ERROR", "Permission grant failed: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Revoke permission from user
     */
    public boolean revokePermission(int revokerId, int userId, Permission permission, 
                                 ResourceType resourceType, int resourceId) {
        
        // Check if revoker has permission to revoke
        if (!hasPermission(revokerId, Permission.REVOKE_PERMISSION, resourceType, resourceId)) {
            AuditLoggerUtil.logSecurityEvent("UNAUTHORIZED_PERMISSION_REVOKE", 
                                      String.format("UserModel %d attempted to revoke permission from user %d", revokerId, userId), 
                                      null);
            return false;
        }
        
        String sql = "UPDATE user_permissions SET is_active = FALSE, revoked_at = CURRENT_TIMESTAMP, " +
                    "revoked_by = ? WHERE user_id = ? AND permission = ? AND resource_type = ? " +
                    "AND resource_id = ? AND is_active = TRUE";
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, revokerId);
            stmt.setInt(2, userId);
            stmt.setString(3, permission.name());
            stmt.setString(4, resourceType.name());
            stmt.setInt(5, resourceId);
            
            int rowsUpdated = stmt.executeUpdate();
            
            if (rowsUpdated > 0) {
                AuditLoggerUtil.logUserAction(revokerId, "PERMISSION_REVOKED", resourceType.name(), resourceId,
                                        String.format("Revoked %s permission from user %d", permission.name(), userId));
                return true;
            }
        } catch (SQLException e) {
            AuditLoggerUtil.logSystemEvent("DATABASE_ERROR", "Permission revoke failed: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Get user permissions
     */
    public List<UserPermission> getUserPermissions(int userId) {
        List<UserPermission> permissions = new ArrayList<>();
        
        String sql = "SELECT up.*, u.name as granted_by_name " +
                    "FROM user_permissions up " +
                    "LEFT JOIN users u ON up.granted_by = u.id " +
                    "WHERE up.user_id = ? AND up.is_active = TRUE " +
                    "AND (up.expires_at IS NULL OR up.expires_at > NOW()) " +
                    "ORDER BY up.created_at DESC";
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, userId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    UserPermission permission = new UserPermission();
                    permission.setId(rs.getInt("id"));
                    permission.setUserId(rs.getInt("user_id"));
                    permission.setPermission(Permission.valueOf(rs.getString("permission")));
                    permission.setResourceType(ResourceType.valueOf(rs.getString("resource_type")));
                    permission.setResourceId(rs.getInt("resource_id"));
                    permission.setGrantedBy(rs.getInt("granted_by"));
                    permission.setGrantedByName(rs.getString("granted_by_name"));
                    permission.setExpiresAt(rs.getTimestamp("expires_at") != null ? 
                                         rs.getTimestamp("expires_at").toLocalDateTime() : null);
                    permission.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                    
                    permissions.add(permission);
                }
            }
        } catch (SQLException e) {
            AuditLoggerUtil.logSystemEvent("DATABASE_ERROR", "Get user permissions failed: " + e.getMessage());
        }
        
        return permissions;
    }
    
    /**
     * Get permissions for a resource
     */
    public List<UserPermission> getResourcePermissions(ResourceType resourceType, int resourceId) {
        List<UserPermission> permissions = new ArrayList<>();
        
        String sql = "SELECT up.*, u.name as user_name " +
                    "FROM user_permissions up " +
                    "JOIN users u ON up.user_id = u.id " +
                    "WHERE up.resource_type = ? AND up.resource_id = ? AND up.is_active = TRUE " +
                    "AND (up.expires_at IS NULL OR up.expires_at > NOW()) " +
                    "ORDER BY u.name";
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, resourceType.name());
            stmt.setInt(2, resourceId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    UserPermission permission = new UserPermission();
                    permission.setId(rs.getInt("id"));
                    permission.setUserId(rs.getInt("user_id"));
                    permission.setUserName(rs.getString("user_name"));
                    permission.setPermission(Permission.valueOf(rs.getString("permission")));
                    permission.setResourceType(resourceType);
                    permission.setResourceId(resourceId);
                    permission.setGrantedBy(rs.getInt("granted_by"));
                    permission.setExpiresAt(rs.getTimestamp("expires_at") != null ? 
                                         rs.getTimestamp("expires_at").toLocalDateTime() : null);
                    permission.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                    
                    permissions.add(permission);
                }
            }
        } catch (SQLException e) {
            AuditLoggerUtil.logSystemEvent("DATABASE_ERROR", "Get resource permissions failed: " + e.getMessage());
        }
        
        return permissions;
    }
    
    /**
     * Clean up expired permissions
     */
    public int cleanupExpiredPermissions() {
        String sql = "UPDATE user_permissions SET is_active = FALSE " +
                    "WHERE expires_at IS NOT NULL AND expires_at < NOW() AND is_active = TRUE";
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            int rowsUpdated = stmt.executeUpdate();
            
            if (rowsUpdated > 0) {
                AuditLoggerUtil.logSystemMaintenance("CLEANUP_EXPIRED_PERMISSIONS", 
                                              String.format("Cleaned up %d expired permissions", rowsUpdated));
            }
            
            return rowsUpdated;
        } catch (SQLException e) {
            AuditLoggerUtil.logSystemEvent("DATABASE_ERROR", "Cleanup expired permissions failed: " + e.getMessage());
        }
        
        return 0;
    }
    
    // Private helper methods
    
    private boolean hasRolePermission(UserModel.UserType userType, Permission permission, ResourceType resourceType) {
        switch (userType) {
            case DOCTOR:
                return hasDoctorPermission(permission, resourceType);
            case PATIENT:
                return hasPatientPermission(permission, resourceType);
            default:
                return false;
        }
    }
    
    private boolean hasDoctorPermission(Permission permission, ResourceType resourceType) {
        // Doctors have broader access
        switch (permission) {
            case VIEW_OWN_FILES:
            case VIEW_SHARED_FILES:
            case UPLOAD_FILES:
            case DOWNLOAD_FILES:
            case SHARE_FILES:
            case SEARCH_FILES:
                return true;
            case DELETE_FILES:
            case MODIFY_FILES:
                return resourceType == ResourceType.MEDICAL_FILE;
            case VIEW_ALL_PATIENTS:
            case MANAGE_SYSTEM:
                return false;
            default:
                return false;
        }
    }
    
    private boolean hasPatientPermission(Permission permission, ResourceType resourceType) {
        // Patients have limited access to their own data
        switch (permission) {
            case VIEW_OWN_FILES:
            case UPLOAD_FILES:
            case DOWNLOAD_FILES:
            case SHARE_FILES:
            case SEARCH_FILES:
                return true;
            case VIEW_SHARED_FILES:
                return true;
            case DELETE_FILES:
            case MODIFY_FILES:
                return resourceType == ResourceType.MEDICAL_FILE;
            default:
                return false;
        }
    }
    
    private boolean hasFilePermission(int userId, Permission permission, int fileId) {
        // Check if user owns the file
        String sql = "SELECT COUNT(*) FROM medical_files WHERE id = ? AND user_id = ?";
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, fileId);
            stmt.setInt(2, userId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    return true; // Owner has all permissions on their own files
                }
            }
        } catch (SQLException e) {
            AuditLoggerUtil.logSystemEvent("DATABASE_ERROR", "File permission check failed: " + e.getMessage());
        }
        
        // Check if file is shared with user
        return new SharingDAO().hasFileAccess(userId, fileId);
    }
    
    private boolean hasProfilePermission(int userId, Permission permission, int profileUserId) {
        // Users can view and modify their own profile
        if (userId == profileUserId) {
            return permission == Permission.VIEW_OWN_PROFILE || permission == Permission.MODIFY_OWN_PROFILE;
        }
        
        // Doctors can view patient profiles if shared
        Optional<UserModel> userOpt = UserDAO.getInstance().getUserById(userId);
        if (userOpt.isPresent() && userOpt.get().isDoctor()) {
            return permission == Permission.VIEW_PATIENT_PROFILE;
        }
        
        return false;
    }
    
    private boolean hasSystemPermission(UserModel.UserType userType, Permission permission) {
        // Only system administrators can manage system configuration
        return false; // In this implementation, no system permissions are granted
    }
    
    private boolean permissionExists(int userId, Permission permission, ResourceType resourceType, int resourceId) {
        String sql = "SELECT COUNT(*) FROM user_permissions " +
                    "WHERE user_id = ? AND permission = ? AND resource_type = ? " +
                    "AND resource_id = ? AND is_active = TRUE";
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, userId);
            stmt.setString(2, permission.name());
            stmt.setString(3, resourceType.name());
            stmt.setInt(4, resourceId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            AuditLoggerUtil.logSystemEvent("DATABASE_ERROR", "Permission existence check failed: " + e.getMessage());
        }
        
        return false;
    }
    
    private boolean updateExistingPermission(int userId, Permission permission, ResourceType resourceType, 
                                         int resourceId, LocalDateTime expiresAt) {
        String sql = "UPDATE user_permissions SET expires_at = ? " +
                    "WHERE user_id = ? AND permission = ? AND resource_type = ? " +
                    "AND resource_id = ? AND is_active = TRUE";
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setTimestamp(1, expiresAt != null ? Timestamp.valueOf(expiresAt) : null);
            stmt.setInt(2, userId);
            stmt.setString(3, permission.name());
            stmt.setString(4, resourceType.name());
            stmt.setInt(5, resourceId);
            
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            AuditLoggerUtil.logSystemEvent("DATABASE_ERROR", "Update existing permission failed: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Permission enum
     */
    public enum Permission {
        VIEW_OWN_FILES,
        VIEW_SHARED_FILES,
        UPLOAD_FILES,
        DOWNLOAD_FILES,
        DELETE_FILES,
        MODIFY_FILES,
        SHARE_FILES,
        SEARCH_FILES,
        VIEW_OWN_PROFILE,
        MODIFY_OWN_PROFILE,
        VIEW_PATIENT_PROFILE,
        VIEW_ALL_PATIENTS,
        GRANT_PERMISSION,
        REVOKE_PERMISSION,
        MANAGE_SYSTEM
    }
    
    /**
     * Resource type enum
     */
    public enum ResourceType {
        MEDICAL_FILE,
        USER_PROFILE,
        SYSTEM_CONFIG
    }
    
    /**
     * UserModel permission class
     */
    public static class UserPermission {
        private int id;
        private int userId;
        private String userName;
        private Permission permission;
        private ResourceType resourceType;
        private int resourceId;
        private int grantedBy;
        private String grantedByName;
        private LocalDateTime expiresAt;
        private LocalDateTime createdAt;
        
        // Getters and setters
        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        
        public int getUserId() { return userId; }
        public void setUserId(int userId) { this.userId = userId; }
        
        public String getUserName() { return userName; }
        public void setUserName(String userName) { this.userName = userName; }
        
        public Permission getPermission() { return permission; }
        public void setPermission(Permission permission) { this.permission = permission; }
        
        public ResourceType getResourceType() { return resourceType; }
        public void setResourceType(ResourceType resourceType) { this.resourceType = resourceType; }
        
        public int getResourceId() { return resourceId; }
        public void setResourceId(int resourceId) { this.resourceId = resourceId; }
        
        public int getGrantedBy() { return grantedBy; }
        public void setGrantedBy(int grantedBy) { this.grantedBy = grantedBy; }
        
        public String getGrantedByName() { return grantedByName; }
        public void setGrantedByName(String grantedByName) { this.grantedByName = grantedByName; }
        
        public LocalDateTime getExpiresAt() { return expiresAt; }
        public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
        
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        
        public boolean isExpired() {
            return expiresAt != null && expiresAt.isBefore(LocalDateTime.now());
        }
        
        public String getPermissionDisplay() {
            return permission.name().replace("_", " ").toLowerCase();
        }
        
        public String getResourceTypeDisplay() {
            return resourceType.name().replace("_", " ").toLowerCase();
        }
    }
}
