package com.healthvault.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * UserModel model representing a patient or doctor account.
 */
public class UserModel {

    public enum UserType { PATIENT, DOCTOR }

    private int id;
    private String name;
    private String email;
    private String passwordHash;
    private String healthId;
    private UserType userType;
    private String phone;
    private LocalDate dateOfBirth;
    private String address;
    private String emergencyContact;
    private String otpSecret;
    private boolean verified;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public UserModel() {}

    public UserModel(String name, String email, String passwordHash, String healthId,
                UserType userType, String phone, LocalDate dateOfBirth,
                String address, String emergencyContact) {
        this.name             = name;
        this.email            = email;
        this.passwordHash     = passwordHash;
        this.healthId         = healthId;
        this.userType         = userType;
        this.phone            = phone;
        this.dateOfBirth      = dateOfBirth;
        this.address          = address;
        this.emergencyContact = emergencyContact;
    }

    // ── Getters & Setters ───────────────────────────────────────────────────────

    public int getId()                       { return id; }
    public void setId(int id)               { this.id = id; }

    public String getName()                  { return name; }
    public void setName(String name)        { this.name = name; }

    public String getEmail()                 { return email; }
    public void setEmail(String email)      { this.email = email; }

    public String getPasswordHash()          { return passwordHash; }
    public void setPasswordHash(String h)   { this.passwordHash = h; }

    public String getHealthId()              { return healthId; }
    public void setHealthId(String hid)     { this.healthId = hid; }

    public UserType getUserType()            { return userType; }
    public void setUserType(UserType t)     { this.userType = t; }

    public String getPhone()                 { return phone; }
    public void setPhone(String phone)      { this.phone = phone; }

    public LocalDate getDateOfBirth()        { return dateOfBirth; }
    public void setDateOfBirth(LocalDate d) { this.dateOfBirth = d; }

    public String getAddress()               { return address; }
    public void setAddress(String address)  { this.address = address; }

    public String getEmergencyContact()                     { return emergencyContact; }
    public void setEmergencyContact(String emergencyContact){ this.emergencyContact = emergencyContact; }

    public String getOtpSecret()             { return otpSecret; }
    public void setOtpSecret(String s)      { this.otpSecret = s; }

    public boolean isVerified()              { return verified; }
    public void setVerified(boolean v)      { this.verified = v; }

    public LocalDateTime getCreatedAt()                  { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt)   { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt()                  { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt)   { this.updatedAt = updatedAt; }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    public boolean isPatient() { return UserType.PATIENT == userType; }
    public boolean isDoctor()  { return UserType.DOCTOR  == userType; }

    @Override
    public String toString() {
        return "UserModel{id=" + id + ", name='" + name + "', email='" + email +
               "', healthId='" + healthId + "', type=" + userType + "}";
    }
}
