package com.healthvault.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Medical file model representing encrypted medical documents
 */
public class MedicalFileModel {
    private int id;
    private int userId;
    private String fileName;
    private String originalFileName;
    private String fileType;
    private long fileSize;
    private String encryptedPath;
    private String fileHash;
    private FileCategory category;
    private String description;
    private String doctorName;
    private String hospitalName;
    private LocalDate uploadDate;
    private List<String> tags;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    public MedicalFileModel() {}
    
    public MedicalFileModel(int userId, String originalFileName, String fileType, long fileSize, 
                      String encryptedPath, String fileHash, FileCategory category, 
                      String description, String doctorName, String hospitalName, 
                      LocalDate uploadDate, List<String> tags) {
        this.userId = userId;
        this.originalFileName = originalFileName;
        this.fileType = fileType;
        this.fileSize = fileSize;
        this.encryptedPath = encryptedPath;
        this.fileHash = fileHash;
        this.category = category;
        this.description = description;
        this.doctorName = doctorName;
        this.hospitalName = hospitalName;
        this.uploadDate = uploadDate;
        this.tags = tags;
    }
    
    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    
    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }
    
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    
    public String getOriginalFileName() { return originalFileName; }
    public void setOriginalFileName(String originalFileName) { this.originalFileName = originalFileName; }
    
    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }
    
    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }
    
    public String getEncryptedPath() { return encryptedPath; }
    public void setEncryptedPath(String encryptedPath) { this.encryptedPath = encryptedPath; }
    
    public String getFileHash() { return fileHash; }
    public void setFileHash(String fileHash) { this.fileHash = fileHash; }
    
    public FileCategory getCategory() { return category; }
    public void setCategory(FileCategory category) { this.category = category; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getDoctorName() { return doctorName; }
    public void setDoctorName(String doctorName) { this.doctorName = doctorName; }
    
    public String getHospitalName() { return hospitalName; }
    public void setHospitalName(String hospitalName) { this.hospitalName = hospitalName; }
    
    public LocalDate getUploadDate() { return uploadDate; }
    public void setUploadDate(LocalDate uploadDate) { this.uploadDate = uploadDate; }
    
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    /**
     * Get file size in human readable format
     */
    public String getFormattedFileSize() {
        if (fileSize < 1024) {
            return fileSize + " B";
        } else if (fileSize < 1024 * 1024) {
            return String.format("%.1f KB", fileSize / 1024.0);
        } else if (fileSize < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", fileSize / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f GB", fileSize / (1024.0 * 1024.0 * 1024.0));
        }
    }
    
    /**
     * Check if file is an image
     */
    public boolean isImage() {
        return fileType != null && (fileType.equalsIgnoreCase("jpg") || 
                                   fileType.equalsIgnoreCase("jpeg") || 
                                   fileType.equalsIgnoreCase("png") || 
                                   fileType.equalsIgnoreCase("gif") || 
                                   fileType.equalsIgnoreCase("bmp"));
    }
    
    /**
     * Check if file is a PDF
     */
    public boolean isPDF() {
        return fileType != null && fileType.equalsIgnoreCase("pdf");
    }
    
    /**
     * Check if file is a document
     */
    public boolean isDocument() {
        return fileType != null && (fileType.equalsIgnoreCase("doc") || 
                                   fileType.equalsIgnoreCase("docx") || 
                                   fileType.equalsIgnoreCase("txt") || 
                                   fileType.equalsIgnoreCase("rtf"));
    }
    
    @Override
    public String toString() {
        return "MedicalFileModel{" +
                "id=" + id +
                ", fileName='" + fileName + '\'' +
                ", originalFileName='" + originalFileName + '\'' +
                ", fileType='" + fileType + '\'' +
                ", fileSize=" + fileSize +
                ", category=" + category +
                ", doctorName='" + doctorName + '\'' +
                ", uploadDate=" + uploadDate +
                '}';
    }
    
    public enum FileCategory {
        PRESCRIPTION("Prescription"),
        LAB_REPORT("Lab Report"),
        IMAGING("Imaging"),
        MEDICAL_HISTORY("Medical History"),
        OTHER("Other");
        
        private final String displayName;
        
        FileCategory(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
}
