package com.healthvault.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.healthvault.model.UserModel;
import com.healthvault.util.HealthIdUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * QR Code generation service for Health IDs and file sharing
 */
public class QRCodeService {
    
    /**
     * Generate QR code for user Health ID
     */
    public String generateHealthIDQR(UserModel user) throws WriterException, IOException {
        String qrData = HealthIdUtil.generateQRCodeData(
            user.getHealthId(), 
            user.getName(), 
            user.getUserType().name()
        );
        
        return generateQRCodeImage(qrData, 300, 300);
    }
    
    /**
     * Generate QR code for file sharing
     */
    public String generateFileShareQR(int fileId, String fileName, String accessToken) 
            throws WriterException, IOException {
        
        String qrData = String.format(
            "HEALTH_VAULT_SHARE|FILE_ID:%d|FILE_NAME:%s|TOKEN:%s|TIMESTAMP:%d",
            fileId, fileName, accessToken, System.currentTimeMillis()
        );
        
        return generateQRCodeImage(qrData, 250, 250);
    }
    
    /**
     * Generate QR code for emergency access
     */
    public String generateEmergencyAccessQR(String emergencyCode, String patientName, String healthId) 
            throws WriterException, IOException {
        
        String qrData = String.format(
            "HEALTH_VAULT_EMERGENCY|CODE:%s|PATIENT:%s|HEALTH_ID:%s|TIMESTAMP:%d",
            emergencyCode, patientName, healthId, System.currentTimeMillis()
        );
        
        return generateQRCodeImage(qrData, 350, 350);
    }
    
    /**
     * Generate QR code for patient registration
     */
    public String generateRegistrationQR(String hospitalCode) throws WriterException, IOException {
        String qrData = String.format(
            "HEALTH_VAULT_REGISTRATION|HOSPITAL:%s|TIMESTAMP:%d",
            hospitalCode, System.currentTimeMillis()
        );
        
        return generateQRCodeImage(qrData, 200, 200);
    }
    
    /**
     * Generate QR code for appointment check-in
     */
    public String generateAppointmentQR(int appointmentId, String patientName, String doctorName) 
            throws WriterException, IOException {
        
        String qrData = String.format(
            "HEALTH_VAULT_APPOINTMENT|ID:%d|PATIENT:%s|DOCTOR:%s|DATE:%s",
            appointmentId, patientName, doctorName, LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        );
        
        return generateQRCodeImage(qrData, 200, 200);
    }
    
    /**
     * Verify and parse QR code data
     */
    public QRCodeData parseQRCodeData(String qrData) {
        if (qrData == null || qrData.trim().isEmpty()) {
            return null;
        }
        
        String[] parts = qrData.split("\\|");
        if (parts.length < 2) {
            return null;
        }
        
        String type = parts[0];
        QRCodeData data = new QRCodeData();
        data.setType(type);
        data.setRawData(qrData);
        
        for (int i = 1; i < parts.length; i++) {
            String[] keyValue = parts[i].split(":", 2);
            if (keyValue.length == 2) {
                data.addField(keyValue[0], keyValue[1]);
            }
        }
        
        return data;
    }
    
    /**
     * Validate QR code timestamp (prevent replay attacks)
     */
    public boolean isValidTimestamp(QRCodeData qrData, long maxAgeMinutes) {
        String timestampStr = qrData.getField("TIMESTAMP");
        if (timestampStr == null) {
            return false;
        }
        
        try {
            long timestamp = Long.parseLong(timestampStr);
            long currentTime = System.currentTimeMillis();
            long ageMinutes = (currentTime - timestamp) / (60 * 1000);
            
            return ageMinutes <= maxAgeMinutes;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    /**
     * Generate QR code image as Base64 string
     */
    private String generateQRCodeImage(String text, int width, int height) 
            throws WriterException, IOException {
        
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        BitMatrix bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, width, height);
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);
        
        byte[] imageBytes = outputStream.toByteArray();
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(imageBytes);
    }
    
    /**
     * Generate QR code with custom colors
     */
    public String generateCustomQRCode(String text, int width, int height, 
                                    String foregroundColor, String backgroundColor) 
            throws WriterException, IOException {
        
        // This would require custom implementation with color support
        // For now, return standard QR code
        return generateQRCodeImage(text, width, height);
    }
    
    /**
     * Generate batch QR codes for multiple users
     */
    public java.util.Map<String, String> generateBatchHealthIDQRs(java.util.List<UserModel> users) {
        java.util.Map<String, String> qrCodes = new java.util.HashMap<>();
        
        for (UserModel user : users) {
            try {
                String qrCode = generateHealthIDQR(user);
                qrCodes.put(user.getHealthId(), qrCode);
            } catch (Exception e) {
                // Log error but continue with other users
                throw new RuntimeException("Failed to generate QR for user " + user.getHealthId() + ": " + e.getMessage());
            }
        }
        
        return qrCodes;
    }
    
    /**
     * QR Code data container class
     */
    public static class QRCodeData {
        private String type;
        private String rawData;
        private java.util.Map<String, String> fields = new java.util.HashMap<>();
        
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public String getRawData() { return rawData; }
        public void setRawData(String rawData) { this.rawData = rawData; }
        
        public void addField(String key, String value) {
            fields.put(key, value);
        }
        
        public String getField(String key) {
            return fields.get(key);
        }
        
        public java.util.Map<String, String> getFields() {
            return new java.util.HashMap<>(fields);
        }
        
        public boolean isHealthIDQR() {
            return "HEALTH_ID".equals(type);
        }
        
        public boolean isFileShareQR() {
            return "HEALTH_VAULT_SHARE".equals(type);
        }
        
        public boolean isEmergencyAccessQR() {
            return "HEALTH_VAULT_EMERGENCY".equals(type);
        }
        
        public boolean isRegistrationQR() {
            return "HEALTH_VAULT_REGISTRATION".equals(type);
        }
        
        public boolean isAppointmentQR() {
            return "HEALTH_VAULT_APPOINTMENT".equals(type);
        }
        
        @Override
        public String toString() {
            return "QRCodeData{" +
                    "type='" + type + '\'' +
                    ", fields=" + fields +
                    '}';
        }
    }
}
