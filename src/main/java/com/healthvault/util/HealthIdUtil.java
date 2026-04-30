package com.healthvault.util;

import java.security.SecureRandom;
import java.time.Year;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Utility class for generating unique Health IDs
 * Format: HV-YYYY-XXXXX where YYYY is current year and XXXXX is sequential number
 */
public class HealthIdUtil {
    private static final SecureRandom secureRandom = new SecureRandom();
    private static final AtomicInteger sequenceNumber = new AtomicInteger(0);
    
    /**
     * Set the initial sequence number based on existing database records
     */
    public static void setInitialSequence(int startValue) {
        sequenceNumber.set(startValue);
    }
    
    /**
     * Generate a new unique Health ID
     */
    public static String generateHealthId() {
        int year = Year.now().getValue();
        int sequence = sequenceNumber.incrementAndGet();
        
        // Reset sequence if it exceeds maximum (e.g. 99999)
        if (sequence > 99999) {
            sequence = 0;
            sequenceNumber.set(sequence);
        }
        
        return String.format("HV-%d-%05d", year, sequence);
    }
    
    /**
     * Generate Health ID with user type prefix
     */
    public static String generateHealthId(String userType) {
        String baseId = generateHealthId();
        return userType.toUpperCase().charAt(0) + "-" + baseId;
    }
    
    /**
     * Validate Health ID format
     */
    public static boolean isValidHealthId(String healthId) {
        if (healthId == null) {
            return false;
        }
        
        // Accept formats: HV-YYYY-XXXXX or P-HV-YYYY-XXXXX or D-HV-YYYY-XXXXX
        return healthId.matches("^[PD]?HV-\\d{4}-\\d{5}$");
    }
    
    /**
     * Extract year from Health ID
     */
    public static int extractYear(String healthId) {
        if (!isValidHealthId(healthId)) {
            throw new IllegalArgumentException("Invalid Health ID format");
        }
        
        String[] parts = healthId.split("-");
        return Integer.parseInt(parts[parts.length - 2]);
    }
    
    /**
     * Generate emergency access code
     */
    public static String generateEmergencyCode() {
        return "EMERGENCY-" + generateRandomString(8).toUpperCase();
    }
    
    /**
     * Generate random string for various purposes
     */
    public static String generateRandomString(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(secureRandom.nextInt(chars.length())));
        }
        
        return sb.toString();
    }
    
    /**
     * Generate temporary access token
     */
    public static String generateAccessToken() {
        return "TOKEN-" + generateRandomString(16).toLowerCase();
    }
    
    /**
     * Generate session ID
     */
    public static String generateSessionId() {
        return "SESSION-" + generateRandomString(32).toLowerCase();
    }
    
    /**
     * Generate file reference number
     */
    public static String generateFileReference() {
        return "FILE-" + System.currentTimeMillis() + "-" + generateRandomString(6);
    }
    
    /**
     * Generate QR code data for Health ID
     */
    public static String generateQRCodeData(String healthId, String name, String userType) {
        return String.format("HEALTH_ID:%s|NAME:%s|TYPE:%s|TIMESTAMP:%d", 
                           healthId, name, userType, System.currentTimeMillis());
    }
}
