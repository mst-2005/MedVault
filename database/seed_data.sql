-- ============================================================
--  Health-ID Vault — Seed Data
--  Passwords:
--    admin@healthvault.com   → Admin@2024
--    dr.*@healthvault.com    → Doctor@123
--    patient.*@example.com   → Patient@123
-- ============================================================

USE health_vault;

-- ── Clear existing seed data (safe re-run) ──────────────────
SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE audit_logs;
TRUNCATE TABLE access_control;
TRUNCATE TABLE medical_files;
TRUNCATE TABLE users;
SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================
--  1. ADMIN
--  Password: Admin@2024
-- ============================================================
INSERT INTO users (name, email, password_hash, health_id, user_type,
                   phone, date_of_birth, address, emergency_contact,
                   otp_secret, is_verified)
VALUES (
  'Rajesh Kumar Sharma',
  'admin@healthvault.com',
  '$2a$12$4sPSkq1MKCm5lG5uvSlYT.zsONsvW9eMLBlLDrubmSML.OivlhClC',
  'HV-2024-00000',
  'DOCTOR',
  '+91-9876543210',
  '1975-08-15',
  '42, MG Road, Connaught Place, New Delhi - 110001',
  'Sunita Sharma | +91-9876543211',
  'ADMIN_OTP_SECRET_001',
  TRUE
);

-- ============================================================
--  2. DOCTORS
--  Password: Doctor@123
-- ============================================================
INSERT INTO users (name, email, password_hash, health_id, user_type,
                   phone, date_of_birth, address, emergency_contact,
                   otp_secret, is_verified)
VALUES
(
  'Dr. Priya Mehta',
  'dr.priya@healthvault.com',
  '$2a$12$lzYSa92r9y91hYHjrWxc..awmAb4MxwYDjr1ulb/LjLs2epc533Jy',
  'HV-2024-00002',
  'DOCTOR',
  '+91-9823401122',
  '1982-03-22',
  '15, Linking Road, Bandra West, Mumbai - 400050',
  'Amit Mehta | +91-9823401123',
  'DOCTOR_OTP_SECRET_002',
  TRUE
),
(
  'Dr. Arjun Nair',
  'dr.arjun@healthvault.com',
  '$2a$12$lzYSa92r9y91hYHjrWxc..awmAb4MxwYDjr1ulb/LjLs2epc533Jy',
  'HV-2024-00003',
  'DOCTOR',
  '+91-9845512233',
  '1979-11-08',
  '7, Indiranagar 100 Feet Road, Bengaluru - 560038',
  'Meera Nair | +91-9845512234',
  'DOCTOR_OTP_SECRET_003',
  TRUE
),
(
  'Dr. Sneha Iyer',
  'dr.sneha@healthvault.com',
  '$2a$12$lzYSa92r9y91hYHjrWxc..awmAb4MxwYDjr1ulb/LjLs2epc533Jy',
  'HV-2024-00004',
  'DOCTOR',
  '+91-9901234567',
  '1985-06-14',
  '22, T. Nagar, Chennai - 600017',
  'Rajan Iyer | +91-9901234568',
  'DOCTOR_OTP_SECRET_004',
  TRUE
);

-- ============================================================
--  3. PATIENTS
--  Password: Patient@123
-- ============================================================
INSERT INTO users (name, email, password_hash, health_id, user_type,
                   phone, date_of_birth, address, emergency_contact,
                   otp_secret, is_verified)
VALUES
(
  'Amit Singh',
  'patient.amit@example.com',
  '$2a$12$vINtNep6QNCW4IlLhbR2guvYvSl2shGV.gMdiH/KMWm2VHtw3qG2S',
  'HV-2024-10001',
  'PATIENT',
  '+91-9711223344',
  '1990-04-17',
  '8B, Sector 12, Dwarka, New Delhi - 110075',
  'Kavita Singh | +91-9711223345',
  'PATIENT_OTP_001',
  TRUE
),
(
  'Ananya Patel',
  'patient.ananya@example.com',
  '$2a$12$vINtNep6QNCW4IlLhbR2guvYvSl2shGV.gMdiH/KMWm2VHtw3qG2S',
  'HV-2024-10002',
  'PATIENT',
  '+91-9833456789',
  '1995-09-30',
  '34, Satellite Road, Ahmedabad - 380015',
  'Nilesh Patel | +91-9833456790',
  'PATIENT_OTP_002',
  TRUE
),
(
  'Mohammed Farhan Khan',
  'patient.farhan@example.com',
  '$2a$12$vINtNep6QNCW4IlLhbR2guvYvSl2shGV.gMdiH/KMWm2VHtw3qG2S',
  'HV-2024-10003',
  'PATIENT',
  '+91-9765432100',
  '1988-12-05',
  '19, Park Street, Kolkata - 700016',
  'Shabana Khan | +91-9765432101',
  'PATIENT_OTP_003',
  TRUE
),
(
  'Lakshmi Venkataraman',
  'patient.lakshmi@example.com',
  '$2a$12$vINtNep6QNCW4IlLhbR2guvYvSl2shGV.gMdiH/KMWm2VHtw3qG2S',
  'HV-2024-10004',
  'PATIENT',
  '+91-9944556677',
  '1975-01-23',
  '56, Adyar, Chennai - 600020',
  'Ramesh Venkataraman | +91-9944556678',
  'PATIENT_OTP_004',
  TRUE
),
(
  'Rohan Desai',
  'patient.rohan@example.com',
  '$2a$12$vINtNep6QNCW4IlLhbR2guvYvSl2shGV.gMdiH/KMWm2VHtw3qG2S',
  'HV-2024-10005',
  'PATIENT',
  '+91-9822334455',
  '2000-07-11',
  '77, FC Road, Shivaji Nagar, Pune - 411004',
  'Priya Desai | +91-9822334456',
  'PATIENT_OTP_005',
  TRUE
);

-- ============================================================
--  4. FAKE MEDICAL FILES (linked to patient IDs 5–9)
--     Note: encrypted_path and file_hash are placeholders
-- ============================================================
INSERT INTO medical_files (user_id, file_name, original_file_name, file_type,
                            file_size, encrypted_path, file_hash, category,
                            description, doctor_name, hospital_name, upload_date, tags)
SELECT id, 
  CONCAT('FILE-', UNIX_TIMESTAMP(), '-', FLOOR(RAND()*9999), '.enc'),
  original_file_name, file_type, file_size,
  CONCAT('vault/placeholder-', id, '.enc'),
  SHA2(CONCAT('hash-placeholder-', id), 256),
  category, description, doctor_name, hospital_name, upload_date, tags
FROM (
  SELECT u.id,
    'blood_report_jan2024.pdf'       AS original_file_name,
    'pdf'                            AS file_type,
    245760                           AS file_size,
    'LAB_REPORT'                     AS category,
    'Complete Blood Count (CBC) — routine annual check' AS description,
    'Dr. Priya Mehta'                AS doctor_name,
    'Apollo Hospitals, Mumbai'       AS hospital_name,
    '2024-01-15'                     AS upload_date,
    '["blood", "CBC", "annual"]'     AS tags
  FROM users u WHERE u.email = 'patient.amit@example.com'
  UNION ALL
  SELECT u.id,
    'chest_xray_feb2024.jpg', 'jpg', 512000,
    'IMAGING',
    'Chest X-Ray — mild upper respiratory infection follow-up',
    'Dr. Arjun Nair', 'Manipal Hospital, Bengaluru',
    '2024-02-10', '["xray","chest","respiratory"]'
  FROM users u WHERE u.email = 'patient.amit@example.com'
  UNION ALL
  SELECT u.id,
    'diabetes_prescription_mar2024.pdf', 'pdf', 128000,
    'PRESCRIPTION',
    'Metformin 500mg twice daily — Type 2 Diabetes management',
    'Dr. Sneha Iyer', 'MIOT International, Chennai',
    '2024-03-05', '["diabetes","prescription","metformin"]'
  FROM users u WHERE u.email = 'patient.ananya@example.com'
  UNION ALL
  SELECT u.id,
    'ecg_report_jan2024.pdf', 'pdf', 184320,
    'LAB_REPORT',
    'ECG report — borderline tachycardia noted, no intervention required',
    'Dr. Priya Mehta', 'Kokilaben Dhirubhai Ambani Hospital',
    '2024-01-28', '["ECG","heart","cardiology"]'
  FROM users u WHERE u.email = 'patient.farhan@example.com'
  UNION ALL
  SELECT u.id,
    'thyroid_test_apr2024.pdf', 'pdf', 98304,
    'LAB_REPORT',
    'Thyroid Function Test (TFT) — TSH slightly elevated, under observation',
    'Dr. Sneha Iyer', 'Apollo Gleneagles, Kolkata',
    '2024-04-12', '["thyroid","TFT","TSH"]'
  FROM users u WHERE u.email = 'patient.lakshmi@example.com'
  UNION ALL
  SELECT u.id,
    'knee_mri_mar2024.jpg', 'jpg', 1048576,
    'IMAGING',
    'MRI Right Knee — Grade II medial meniscus tear, physiotherapy recommended',
    'Dr. Arjun Nair', 'Fortis Hospital, Pune',
    '2024-03-20', '["MRI","knee","ortho"]'
  FROM users u WHERE u.email = 'patient.rohan@example.com'
  UNION ALL
  SELECT u.id,
    'medical_history_summary.pdf', 'pdf', 307200,
    'MEDICAL_HISTORY',
    'Comprehensive medical history — hypertension since 2018, on Amlodipine 5mg',
    'Dr. Priya Mehta', 'Lilavati Hospital, Mumbai',
    '2024-02-01', '["history","hypertension","chronic"]'
  FROM users u WHERE u.email = 'patient.lakshmi@example.com'
) AS seed_data;

-- ============================================================
--  5. FILE SHARING — Amit's blood report shared with Dr. Priya
-- ============================================================
INSERT INTO access_control (file_id, owner_id, shared_with_user_id, access_type,
                             expires_at, is_active)
SELECT
  mf.id,
  mf.user_id,
  doc.id,
  'READ',
  DATE_ADD(NOW(), INTERVAL 30 DAY),
  TRUE
FROM medical_files mf
JOIN users owner ON mf.user_id = owner.id AND owner.email = 'patient.amit@example.com'
JOIN users doc   ON doc.email  = 'dr.priya@healthvault.com'
WHERE mf.original_file_name = 'blood_report_jan2024.pdf';

-- ============================================================
--  Confirmation
-- ============================================================
SELECT user_type, COUNT(*) AS total FROM users GROUP BY user_type;
SELECT 'medical_files' AS tbl, COUNT(*) AS total FROM medical_files
UNION ALL
SELECT 'access_control', COUNT(*) FROM access_control;
