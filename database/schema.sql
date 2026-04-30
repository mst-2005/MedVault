-- Cryptographically Secure Health-ID Vault Database Schema
-- MySQL 8.0+ compatible

CREATE DATABASE IF NOT EXISTS health_vault CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE health_vault;

-- Users table - Stores patient and doctor information
CREATE TABLE users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    health_id VARCHAR(50) UNIQUE NOT NULL,
    user_type ENUM('PATIENT', 'DOCTOR') NOT NULL,
    phone VARCHAR(20),
    date_of_birth DATE,
    address TEXT,
    emergency_contact VARCHAR(255),
    otp_secret VARCHAR(32),
    is_verified BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_health_id (health_id),
    INDEX idx_email (email),
    INDEX idx_user_type (user_type)
);

-- Encryption keys table - Stores user-specific encryption keys
CREATE TABLE encryption_keys (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    key_name VARCHAR(100) NOT NULL,
    encrypted_key BLOB NOT NULL,
    key_algorithm VARCHAR(50) DEFAULT 'AES',
    key_size INT DEFAULT 256,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id)
);

-- Medical files table - Stores encrypted file information
CREATE TABLE medical_files (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    file_type VARCHAR(50) NOT NULL,
    file_size BIGINT NOT NULL,
    encrypted_path VARCHAR(500) NOT NULL,
    file_hash VARCHAR(64) NOT NULL,
    category ENUM('PRESCRIPTION', 'LAB_REPORT', 'IMAGING', 'MEDICAL_HISTORY', 'OTHER') NOT NULL,
    description TEXT,
    doctor_name VARCHAR(255),
    hospital_name VARCHAR(255),
    upload_date DATE,
    tags JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_category (category),
    INDEX idx_upload_date (upload_date),
    INDEX idx_doctor_name (doctor_name)
);

-- Access control table - Manages file sharing permissions
CREATE TABLE access_control (
    id INT AUTO_INCREMENT PRIMARY KEY,
    file_id INT NOT NULL,
    owner_id INT NOT NULL,
    shared_with_user_id INT,
    access_token VARCHAR(255) UNIQUE,
    access_type ENUM('READ', 'READ_WRITE') DEFAULT 'READ',
    expires_at TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (file_id) REFERENCES medical_files(id) ON DELETE CASCADE,
    FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (shared_with_user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_file_id (file_id),
    INDEX idx_owner_id (owner_id),
    INDEX idx_shared_with_user_id (shared_with_user_id),
    INDEX idx_access_token (access_token),
    INDEX idx_expires_at (expires_at)
);

-- Audit logs table - Tracks all system activities
CREATE TABLE audit_logs (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT,
    action VARCHAR(100) NOT NULL,
    target_type VARCHAR(50),
    target_id INT,
    ip_address VARCHAR(45),
    user_agent TEXT,
    details JSON,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_user_id (user_id),
    INDEX idx_action (action),
    INDEX idx_timestamp (timestamp),
    INDEX idx_target_type_id (target_type, target_id)
);

-- Session management table
CREATE TABLE user_sessions (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    session_token VARCHAR(255) UNIQUE NOT NULL,
    ip_address VARCHAR(45),
    user_agent TEXT,
    expires_at TIMESTAMP NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_session_token (session_token),
    INDEX idx_expires_at (expires_at)
);

-- Emergency access table
CREATE TABLE emergency_access (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    emergency_code VARCHAR(255) NOT NULL,
    access_granted_to VARCHAR(255),
    access_reason TEXT,
    expires_at TIMESTAMP,
    is_used BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_emergency_code (emergency_code)
);

-- File sharing requests table
CREATE TABLE sharing_requests (
    id INT AUTO_INCREMENT PRIMARY KEY,
    file_id INT NOT NULL,
    requester_id INT NOT NULL,
    owner_id INT NOT NULL,
    request_message TEXT,
    status ENUM('PENDING', 'APPROVED', 'REJECTED') DEFAULT 'PENDING',
    expires_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (file_id) REFERENCES medical_files(id) ON DELETE CASCADE,
    FOREIGN KEY (requester_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_file_id (file_id),
    INDEX idx_requester_id (requester_id),
    INDEX idx_owner_id (owner_id),
    INDEX idx_status (status)
);

-- Insert default admin user (password: admin123)
INSERT INTO users (name, email, password_hash, health_id, user_type, is_verified) VALUES 
('System Administrator', 'admin@healthvault.com', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi.', 'HV-ADMIN-001', 'DOCTOR', TRUE);

-- Create stored procedures for common operations
DELIMITER //

-- Procedure to get user files with access control
CREATE PROCEDURE GetUserAccessibleFiles(IN userId INT, IN targetUserId INT)
BEGIN
    SELECT mf.*, 
           CASE 
               WHEN mf.user_id = userId THEN 'OWNER'
               WHEN ac.shared_with_user_id = userId AND ac.is_active = TRUE AND (ac.expires_at IS NULL OR ac.expires_at > NOW()) THEN 'SHARED'
               ELSE 'NO_ACCESS'
           END as access_level
    FROM medical_files mf
    LEFT JOIN access_control ac ON mf.id = ac.file_id AND ac.shared_with_user_id = userId
    WHERE (mf.user_id = targetUserId OR ac.shared_with_user_id = userId)
    ORDER BY mf.created_at DESC;
END //

-- Procedure to log audit events
CREATE PROCEDURE LogAuditEvent(
    IN userId INT,
    IN action VARCHAR(100),
    IN targetType VARCHAR(50),
    IN targetId INT,
    IN ipAddress VARCHAR(45),
    IN userAgent TEXT,
    IN details JSON
)
BEGIN
    INSERT INTO audit_logs (user_id, action, target_type, target_id, ip_address, user_agent, details)
    VALUES (userId, action, targetType, targetId, ipAddress, userAgent, details);
END //

DELIMITER ;

-- Create views for common queries
CREATE VIEW user_files_summary AS
SELECT 
    u.id as user_id,
    u.name as user_name,
    u.health_id,
    COUNT(mf.id) as total_files,
    SUM(mf.file_size) as total_size,
    COUNT(CASE WHEN mf.category = 'PRESCRIPTION' THEN 1 END) as prescriptions,
    COUNT(CASE WHEN mf.category = 'LAB_REPORT' THEN 1 END) as lab_reports,
    COUNT(CASE WHEN mf.category = 'IMAGING' THEN 1 END) as imaging,
    COUNT(CASE WHEN mf.category = 'MEDICAL_HISTORY' THEN 1 END) as medical_history
FROM users u
LEFT JOIN medical_files mf ON u.id = mf.user_id
GROUP BY u.id, u.name, u.health_id;

CREATE VIEW recent_activities AS
SELECT 
    al.*,
    u.name as user_name,
    u.health_id
FROM audit_logs al
LEFT JOIN users u ON al.user_id = u.id
ORDER BY al.timestamp DESC
LIMIT 100;
