package com.healthvault.service;

import com.healthvault.config.DatabaseConfig;
import com.healthvault.crypto.PasswordService;
import com.healthvault.model.UserModel;
import com.healthvault.util.AuditLoggerUtil;
import com.healthvault.util.HealthIdUtil;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.healthvault.exception.AuthenticationException;
import com.healthvault.exception.InvalidUserModelException;
import com.healthvault.exception.UserModelException;
import com.healthvault.exception.UserModelNotFoundException;

/**
 * Data Access Object for UserModel management
 */
public class UserDAO {
    private static UserDAO instance;

    /**
     * Get the Singleton instance of UserDAO
     */
    public static synchronized UserDAO getInstance() {
        if (instance == null) {
            instance = new UserDAO();
        }
        return instance;
    }

    private UserDAO() {}

    public UserModel registerUser(String name, String email, String password,
            UserModel.UserType userType, String phone,
            java.time.LocalDate dateOfBirth, String address,
            String emergencyContact) throws Exception {

        if (name == null || name.trim().isEmpty()) {
            throw new InvalidUserModelException("Name is required");
        }
        if (email == null || !isValidEmail(email)) {
            throw new InvalidUserModelException("Valid email is required");
        }
        if (password == null || password.length() < 8) {
            throw new InvalidUserModelException("Password must be at least 8 characters");
        }

        PasswordService.PasswordStrength strength = PasswordService.checkPasswordStrength(password);
        if (strength == PasswordService.PasswordStrength.WEAK) {
            throw new InvalidUserModelException("Password is too weak.");
        }

        if (emailExists(email)) {
            throw new InvalidUserModelException("Email already registered");
        }

        String passwordHash = PasswordService.hashPassword(password);
        String healthId = HealthIdUtil.generateHealthId();
        while (healthIdExists(healthId)) {
            healthId = HealthIdUtil.generateHealthId();
        }

        String otpSecret = PasswordService.generateSecureToken(32);

        UserModel user = new UserModel(name, email, passwordHash, healthId, userType,
                phone, dateOfBirth, address, emergencyContact);
        user.setOtpSecret(otpSecret);
        user.setId(saveUserToDatabase(user));

        AuditLoggerUtil.logUserAction(user.getId(), "USER_REGISTRATION", "USER", user.getId(),
                "UserModel registered with Health ID: " + healthId);

        return user;
    }

    public UserModel authenticateUser(String email, String password) throws Exception {
        String sql = "SELECT * FROM users WHERE email = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, email);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String storedHash = rs.getString("password_hash");
                    if (PasswordService.verifyPassword(password, storedHash)) {
                        UserModel user = mapResultSetToUser(rs);
                        AuditLoggerUtil.logUserAction(user.getId(), "USER_LOGIN", "USER", user.getId(),
                                "UserModel logged in successfully");
                        return user;
                    } else {
                        AuditLoggerUtil.logSystemEvent("FAILED_LOGIN_ATTEMPT", "Incorrect password for email: " + email);
                        throw new AuthenticationException("Password is wrong");
                    }
                } else {
                    AuditLoggerUtil.logSystemEvent("FAILED_LOGIN_ATTEMPT", "Email not found: " + email);
                    throw new UserModelNotFoundException("Email id does not exist");
                }
            }
        } catch (SQLException e) {
            throw new UserModelException("Database connection error", e);
        }
    }

    public String generateOTP(int userId) throws Exception {
        String otp = PasswordService.generateOTP();
        AuditLoggerUtil.logUserAction(userId, "OTP_GENERATED", "USER", userId, "OTP generated for 2FA");
        return otp;
    }

    public boolean verifyOTP(int userId, String providedOTP) {
        String sql = "SELECT otp_secret FROM users WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    AuditLoggerUtil.logUserAction(userId, "OTP_VERIFICATION", "USER", userId, "OTP verification attempted");
                    return providedOTP != null && providedOTP.matches("\\d{6}");
                }
            }
        } catch (SQLException e) {
            AuditLoggerUtil.logSystemEvent("DATABASE_ERROR", "OTP verification failed: " + e.getMessage());
        }
        return false;
    }

    public Optional<UserModel> getUserById(int userId) {
        String sql = "SELECT * FROM users WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToUser(rs));
                }
            }
        } catch (SQLException e) {
            AuditLoggerUtil.logSystemEvent("DATABASE_ERROR", "Get user by ID failed: " + e.getMessage());
        }
        return Optional.empty();
    }

    public Optional<UserModel> getUserByHealthId(String healthId) {
        String sql = "SELECT * FROM users WHERE health_id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, healthId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToUser(rs));
                }
            }
        } catch (SQLException e) {
            AuditLoggerUtil.logSystemEvent("DATABASE_ERROR", "Get user by Health ID failed: " + e.getMessage());
        }
        return Optional.empty();
    }

    public boolean updateUserProfile(UserModel user) {
        String sql = "UPDATE users SET name = ?, phone = ?, address = ?, " +
                "emergency_contact = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, user.getName());
            stmt.setString(2, user.getPhone());
            stmt.setString(3, user.getAddress());
            stmt.setString(4, user.getEmergencyContact());
            stmt.setInt(5, user.getId());
            int rowsUpdated = stmt.executeUpdate();
            if (rowsUpdated > 0) {
                AuditLoggerUtil.logUserAction(user.getId(), "PROFILE_UPDATE", "USER", user.getId(), "UserModel profile updated");
                return true;
            } else {
                throw new UserModelNotFoundException("UserModel with ID " + user.getId() + " not found for update.");
            }
        } catch (SQLException e) {
            throw new UserModelException("Profile update failed", e);
        }
    }

    public boolean deleteUser(int userId) {
        String sql = "DELETE FROM users WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            int rowsDeleted = stmt.executeUpdate();
            if (rowsDeleted > 0) {
                AuditLoggerUtil.logUserAction(userId, "USER_DELETE", "USER", userId, "UserModel deleted successfully");
                return true;
            } else {
                throw new UserModelNotFoundException("UserModel with ID " + userId + " not found for deletion.");
            }
        } catch (SQLException e) {
            throw new UserModelException("UserModel deletion failed", e);
        }
    }

    public List<UserModel> searchUsers(String searchTerm, UserModel.UserType userType) {
        List<UserModel> users = new ArrayList<>();
        String sql = "SELECT * FROM users WHERE (name LIKE ? OR health_id LIKE ?) " +
                "AND user_type = ? AND is_verified = TRUE ORDER BY name";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            String searchPattern = "%" + searchTerm + "%";
            stmt.setString(1, searchPattern);
            stmt.setString(2, searchPattern);
            stmt.setString(3, userType.name());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    users.add(mapResultSetToUser(rs));
                }
            }
        } catch (SQLException e) {
            AuditLoggerUtil.logSystemEvent("DATABASE_ERROR", "UserModel search failed: " + e.getMessage());
        }
        return users;
    }

    public List<UserModel> getAllUsers() {
        List<UserModel> users = new ArrayList<>();
        String sql = "SELECT * FROM users ORDER BY created_at DESC";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                users.add(mapResultSetToUser(rs));
            }
        } catch (SQLException e) {
            AuditLoggerUtil.logSystemEvent("DATABASE_ERROR", "Get all users failed: " + e.getMessage());
        }
        return users;
    }

    public boolean adminChangePassword(int userId, String newPassword) {
        if (newPassword == null || newPassword.length() < 8) return false;
        String newPasswordHash = PasswordService.hashPassword(newPassword);
        String sql = "UPDATE users SET password_hash = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, newPasswordHash);
            stmt.setInt(2, userId);
            int rowsUpdated = stmt.executeUpdate();
            if (rowsUpdated > 0) {
                AuditLoggerUtil.logUserAction(userId, "ADMIN_PASSWORD_RESET", "USER", userId, "Password reset by administrator");
                return true;
            }
        } catch (SQLException e) {
            AuditLoggerUtil.logSystemEvent("DATABASE_ERROR", "Admin password change failed: " + e.getMessage());
        }
        return false;
    }

    public boolean changePassword(int userId, String currentPassword, String newPassword) {
        if (newPassword == null || newPassword.length() < 8) return false;
        Optional<UserModel> userOpt = getUserById(userId);
        if (!userOpt.isPresent()) return false;
        UserModel user = userOpt.get();
        if (!PasswordService.verifyPassword(currentPassword, user.getPasswordHash())) return false;
        String newPasswordHash = PasswordService.hashPassword(newPassword);
        String sql = "UPDATE users SET password_hash = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, newPasswordHash);
            stmt.setInt(2, userId);
            int rowsUpdated = stmt.executeUpdate();
            if (rowsUpdated > 0) {
                AuditLoggerUtil.logUserAction(userId, "PASSWORD_CHANGE", "USER", userId, "Password changed successfully");
                return true;
            }
        } catch (SQLException e) {
            AuditLoggerUtil.logSystemEvent("DATABASE_ERROR", "Password change failed: " + e.getMessage());
        }
        return false;
    }

    public int getMaxHealthIdSequence() {
        String sql = "SELECT MAX(CAST(SUBSTRING_INDEX(health_id, '-', -1) AS UNSIGNED)) FROM users WHERE health_id LIKE 'HV-%'";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            AuditLoggerUtil.logSystemEvent("DATABASE_ERROR", "Failed to fetch max health id sequence: " + e.getMessage());
        }
        return 0;
    }

    private int saveUserToDatabase(UserModel user) throws SQLException {
        String sql = "INSERT INTO users (name, email, password_hash, health_id, user_type, " +
                "phone, date_of_birth, address, emergency_contact, otp_secret, is_verified) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, user.getName());
            stmt.setString(2, user.getEmail());
            stmt.setString(3, user.getPasswordHash());
            stmt.setString(4, user.getHealthId());
            stmt.setString(5, user.getUserType().name());
            stmt.setString(6, user.getPhone());
            stmt.setDate(7, user.getDateOfBirth() != null ? Date.valueOf(user.getDateOfBirth()) : null);
            stmt.setString(8, user.getAddress());
            stmt.setString(9, user.getEmergencyContact());
            stmt.setString(10, user.getOtpSecret());
            stmt.setBoolean(11, user.isVerified());
            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        return generatedKeys.getInt(1);
                    }
                }
            }
        }
        throw new SQLException("Failed to save user to database");
    }

    private boolean emailExists(String email) throws SQLException {
        String sql = "SELECT COUNT(*) FROM users WHERE email = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, email);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }

    private boolean healthIdExists(String healthId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM users WHERE health_id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, healthId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }

    private UserModel mapResultSetToUser(ResultSet rs) throws SQLException {
        UserModel user = new UserModel();
        user.setId(rs.getInt("id"));
        user.setName(rs.getString("name"));
        user.setEmail(rs.getString("email"));
        user.setPasswordHash(rs.getString("password_hash"));
        user.setHealthId(rs.getString("health_id"));
        user.setUserType(UserModel.UserType.valueOf(rs.getString("user_type")));
        user.setPhone(rs.getString("phone"));
        Date dob = rs.getDate("date_of_birth");
        if (dob != null) user.setDateOfBirth(dob.toLocalDate());
        user.setAddress(rs.getString("address"));
        user.setEmergencyContact(rs.getString("emergency_contact"));
        user.setOtpSecret(rs.getString("otp_secret"));
        user.setVerified(rs.getBoolean("is_verified"));
        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) user.setCreatedAt(created.toLocalDateTime());
        Timestamp updated = rs.getTimestamp("updated_at");
        if (updated != null) user.setUpdatedAt(updated.toLocalDateTime());
        return user;
    }

    private boolean isValidEmail(String email) {
        return email != null && email.matches("^[A-Za-z0-9+_.-]+@(.+)$");
    }
}
