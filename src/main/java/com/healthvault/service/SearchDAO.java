package com.healthvault.service;

import com.healthvault.config.DatabaseConfig;
import com.healthvault.model.MedicalFileModel;
import com.healthvault.model.UserModel;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Advanced search service for medical files and users
 */
public class SearchDAO {
    
    /**
     * Search medical files with advanced filters
     */
    public List<MedicalFileModel> searchMedicalFiles(int userId, SearchCriteria criteria) {
        List<MedicalFileModel> files = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
            "SELECT DISTINCT mf.* FROM medical_files mf " +
            "LEFT JOIN access_control ac ON mf.id = ac.file_id " +
            "WHERE (mf.user_id = ? OR (ac.shared_with_user_id = ? AND ac.is_active = TRUE " +
            "AND (ac.expires_at IS NULL OR ac.expires_at > NOW())))"
        );
        
        List<Object> parameters = new ArrayList<>();
        parameters.add(userId);
        parameters.add(userId);
        
        // Add file name search
        if (criteria.getFileName() != null && !criteria.getFileName().trim().isEmpty()) {
            sql.append(" AND mf.original_file_name LIKE ?");
            parameters.add("%" + criteria.getFileName() + "%");
        }
        
        // Add category filter
        if (criteria.getCategory() != null) {
            sql.append(" AND mf.category = ?");
            parameters.add(criteria.getCategory().name());
        }
        
        // Add date range filter
        if (criteria.getStartDate() != null) {
            sql.append(" AND mf.upload_date >= ?");
            parameters.add(Date.valueOf(criteria.getStartDate()));
        }
        
        if (criteria.getEndDate() != null) {
            sql.append(" AND mf.upload_date <= ?");
            parameters.add(Date.valueOf(criteria.getEndDate()));
        }
        
        // Add doctor name filter
        if (criteria.getDoctorName() != null && !criteria.getDoctorName().trim().isEmpty()) {
            sql.append(" AND mf.doctor_name LIKE ?");
            parameters.add("%" + criteria.getDoctorName() + "%");
        }
        
        // Add hospital name filter
        if (criteria.getHospitalName() != null && !criteria.getHospitalName().trim().isEmpty()) {
            sql.append(" AND mf.hospital_name LIKE ?");
            parameters.add("%" + criteria.getHospitalName() + "%");
        }
        
        // Add file type filter
        if (criteria.getFileType() != null && !criteria.getFileType().trim().isEmpty()) {
            sql.append(" AND mf.file_type = ?");
            parameters.add(criteria.getFileType());
        }
        
        // Add description search
        if (criteria.getDescription() != null && !criteria.getDescription().trim().isEmpty()) {
            sql.append(" AND mf.description LIKE ?");
            parameters.add("%" + criteria.getDescription() + "%");
        }
        
        // Add tags search
        if (criteria.getTags() != null && !criteria.getTags().isEmpty()) {
            for (String tag : criteria.getTags()) {
                sql.append(" AND mf.tags LIKE ?");
                parameters.add("%" + tag + "%");
            }
        }
        
        // Add file size range
        if (criteria.getMinSize() != null) {
            sql.append(" AND mf.file_size >= ?");
            parameters.add(criteria.getMinSize());
        }
        
        if (criteria.getMaxSize() != null) {
            sql.append(" AND mf.file_size <= ?");
            parameters.add(criteria.getMaxSize());
        }
        
        // Add sorting
        sql.append(" ORDER BY ").append(getSortClause(criteria.getSortBy(), criteria.getSortOrder()));
        
        // Add pagination
        if (criteria.getLimit() != null && criteria.getLimit() > 0) {
            sql.append(" LIMIT ?");
            parameters.add(criteria.getLimit());
            
            if (criteria.getOffset() != null && criteria.getOffset() > 0) {
                sql.append(" OFFSET ?");
                parameters.add(criteria.getOffset());
            }
        }
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            
            for (int i = 0; i < parameters.size(); i++) {
                stmt.setObject(i + 1, parameters.get(i));
            }
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    files.add(mapResultSetToMedicalFile(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database search failed: " + e.getMessage());
        }
        
        return files;
    }
    
    /**
     * Search users by various criteria
     */
    public List<UserModel> searchUsers(String searchTerm, UserModel.UserType userType, boolean onlyVerified) {
        List<UserModel> users = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM users WHERE 1=1");
        
        List<Object> parameters = new ArrayList<>();
        
        if (searchTerm != null && !searchTerm.trim().isEmpty()) {
            sql.append(" AND (name LIKE ? OR email LIKE ? OR health_id LIKE ?)");
            String searchPattern = "%" + searchTerm + "%";
            for (int i = 0; i < 3; i++) {
                parameters.add(searchPattern);
            }
        }
        
        if (userType != null) {
            sql.append(" AND user_type = ?");
            parameters.add(userType.name());
        }
        
        if (onlyVerified) {
            sql.append(" AND is_verified = TRUE");
        }
        
        sql.append(" ORDER BY name");
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            
            for (int i = 0; i < parameters.size(); i++) {
                stmt.setObject(i + 1, parameters.get(i));
            }
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    users.add(mapResultSetToUser(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("UserModel search failed: " + e.getMessage());
        }
        
        return users;
    }
    
    /**
     * Get search suggestions based on partial input
     */
    public List<String> getSearchSuggestions(int userId, String query, String type) {
        List<String> suggestions = new ArrayList<>();
        
        String sql;
        switch (type.toLowerCase()) {
            case "filename":
                sql = "SELECT DISTINCT original_file_name FROM medical_files " +
                      "WHERE user_id = ? AND original_file_name LIKE ? LIMIT 10";
                break;
            case "doctor":
                sql = "SELECT DISTINCT doctor_name FROM medical_files " +
                      "WHERE user_id = ? AND doctor_name LIKE ? LIMIT 10";
                break;
            case "hospital":
                sql = "SELECT DISTINCT hospital_name FROM medical_files " +
                      "WHERE user_id = ? AND hospital_name LIKE ? LIMIT 10";
                break;
            case "tags":
                sql = "SELECT DISTINCT tags FROM medical_files " +
                      "WHERE user_id = ? AND tags LIKE ? LIMIT 10";
                break;
            default:
                return suggestions;
        }
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, userId);
            stmt.setString(2, "%" + query + "%");
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String value = rs.getString(1);
                    if (value != null && !value.trim().isEmpty()) {
                        if ("tags".equalsIgnoreCase(type)) {
                            // Parse tags from JSON and add individual suggestions
                            suggestions.addAll(parseTagsFromJson(value));
                        } else {
                            suggestions.add(value);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Search suggestions failed: " + e.getMessage());
        }
        
        return suggestions;
    }
    
    /**
     * Get popular search terms for a user
     */
    public List<String> getPopularSearchTerms(int userId) {
        List<String> terms = new ArrayList<>();
        
        // This would typically analyze search history or audit logs
        // For now, return common medical terms
        terms.add("Blood Test");
        terms.add("X-Ray");
        terms.add("Prescription");
        terms.add("MRI");
        terms.add("CT Scan");
        terms.add("Ultrasound");
        terms.add("ECG");
        terms.add("Lab Report");
        
        return terms;
    }
    
    /**
     * Get file count by category for a user
     */
    public List<CategoryCount> getFileCountByCategory(int userId) {
        List<CategoryCount> counts = new ArrayList<>();
        
        String sql = "SELECT category, COUNT(*) as count FROM medical_files " +
                    "WHERE user_id = ? GROUP BY category ORDER BY count DESC";
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, userId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    MedicalFileModel.FileCategory category = MedicalFileModel.FileCategory.valueOf(rs.getString("category"));
                    int count = rs.getInt("count");
                    counts.add(new CategoryCount(category, count));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Category count failed: " + e.getMessage());
        }
        
        return counts;
    }
    
    /**
     * Get recent files for quick access
     */
    public List<MedicalFileModel> getRecentFiles(int userId, int limit) {
        List<MedicalFileModel> files = new ArrayList<>();
        
        String sql = "SELECT * FROM medical_files WHERE user_id = ? " +
                    "ORDER BY created_at DESC LIMIT ?";
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, userId);
            stmt.setInt(2, limit);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    files.add(mapResultSetToMedicalFile(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Recent files query failed: " + e.getMessage());
        }
        
        return files;
    }
    
    // Helper methods
    
    private String getSortClause(String sortBy, String sortOrder) {
        if (sortBy == null) sortBy = "created_at";
        
        // Sanitize sortOrder to prevent SQL injection
        if (sortOrder == null || (!sortOrder.equalsIgnoreCase("ASC") && !sortOrder.equalsIgnoreCase("DESC"))) {
            sortOrder = "DESC";
        }
        
        switch (sortBy.toLowerCase()) {
            case "name":
                return "mf.original_file_name " + sortOrder;
            case "size":
                return "mf.file_size " + sortOrder;
            case "date":
                return "mf.upload_date " + sortOrder;
            case "category":
                return "mf.category " + sortOrder;
            case "doctor":
                return "mf.doctor_name " + sortOrder;
            default:
                return "mf.created_at " + sortOrder;
        }
    }
    
    private MedicalFileModel mapResultSetToMedicalFile(ResultSet rs) throws SQLException {
        MedicalFileModel file = new MedicalFileModel();
        file.setId(rs.getInt("id"));
        file.setUserId(rs.getInt("user_id"));
        file.setFileName(rs.getString("file_name"));
        file.setOriginalFileName(rs.getString("original_file_name"));
        file.setFileType(rs.getString("file_type"));
        file.setFileSize(rs.getLong("file_size"));
        file.setEncryptedPath(rs.getString("encrypted_path"));
        file.setFileHash(rs.getString("file_hash"));
        file.setCategory(MedicalFileModel.FileCategory.valueOf(rs.getString("category")));
        file.setDescription(rs.getString("description"));
        file.setDoctorName(rs.getString("doctor_name"));
        file.setHospitalName(rs.getString("hospital_name"));
        
        Date uploadDate = rs.getDate("upload_date");
        if (uploadDate != null) {
            file.setUploadDate(uploadDate.toLocalDate());
        }
        
        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) {
            file.setCreatedAt(created.toLocalDateTime());
        }
        
        Timestamp updated = rs.getTimestamp("updated_at");
        if (updated != null) {
            file.setUpdatedAt(updated.toLocalDateTime());
        }
        
        return file;
    }
    
    private UserModel mapResultSetToUser(ResultSet rs) throws SQLException {
        UserModel user = new UserModel();
        user.setId(rs.getInt("id"));
        user.setName(rs.getString("name"));
        user.setEmail(rs.getString("email"));
        user.setHealthId(rs.getString("health_id"));
        user.setUserType(UserModel.UserType.valueOf(rs.getString("user_type")));
        user.setPhone(rs.getString("phone"));
        user.setVerified(rs.getBoolean("is_verified"));
        
        Date dob = rs.getDate("date_of_birth");
        if (dob != null) {
            user.setDateOfBirth(dob.toLocalDate());
        }
        
        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) {
            user.setCreatedAt(created.toLocalDateTime());
        }
        
        return user;
    }
    
    private List<String> parseTagsFromJson(String tagsJson) {
        List<String> tags = new ArrayList<>();
        if (tagsJson != null && tagsJson.startsWith("[") && tagsJson.endsWith("]")) {
            String content = tagsJson.substring(1, tagsJson.length() - 1);
            String[] tagArray = content.split(",");
            for (String tag : tagArray) {
                String cleanTag = tag.trim().replaceAll("[\"\\[\\]]", "");
                if (!cleanTag.isEmpty()) {
                    tags.add(cleanTag);
                }
            }
        }
        return tags;
    }
    
    /**
     * Search criteria class
     */
    public static class SearchCriteria {
        private String fileName;
        private MedicalFileModel.FileCategory category;
        private LocalDate startDate;
        private LocalDate endDate;
        private String doctorName;
        private String hospitalName;
        private String fileType;
        private String description;
        private List<String> tags;
        private Long minSize;
        private Long maxSize;
        private String sortBy;
        private String sortOrder;
        private Integer limit;
        private Integer offset;
        
        // Getters and setters
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        
        public MedicalFileModel.FileCategory getCategory() { return category; }
        public void setCategory(MedicalFileModel.FileCategory category) { this.category = category; }
        
        public LocalDate getStartDate() { return startDate; }
        public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
        
        public LocalDate getEndDate() { return endDate; }
        public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
        
        public String getDoctorName() { return doctorName; }
        public void setDoctorName(String doctorName) { this.doctorName = doctorName; }
        
        public String getHospitalName() { return hospitalName; }
        public void setHospitalName(String hospitalName) { this.hospitalName = hospitalName; }
        
        public String getFileType() { return fileType; }
        public void setFileType(String fileType) { this.fileType = fileType; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public List<String> getTags() { return tags; }
        public void setTags(List<String> tags) { this.tags = tags; }
        
        public Long getMinSize() { return minSize; }
        public void setMinSize(Long minSize) { this.minSize = minSize; }
        
        public Long getMaxSize() { return maxSize; }
        public void setMaxSize(Long maxSize) { this.maxSize = maxSize; }
        
        public String getSortBy() { return sortBy; }
        public void setSortBy(String sortBy) { this.sortBy = sortBy; }
        
        public String getSortOrder() { return sortOrder; }
        public void setSortOrder(String sortOrder) { this.sortOrder = sortOrder; }
        
        public Integer getLimit() { return limit; }
        public void setLimit(Integer limit) { this.limit = limit; }
        
        public Integer getOffset() { return offset; }
        public void setOffset(Integer offset) { this.offset = offset; }
    }
    
    /**
     * Category count class
     */
    public static class CategoryCount {
        private final MedicalFileModel.FileCategory category;
        private final int count;
        
        public CategoryCount(MedicalFileModel.FileCategory category, int count) {
            this.category = category;
            this.count = count;
        }
        
        public MedicalFileModel.FileCategory getCategory() { return category; }
        public int getCount() { return count; }
    }
}
