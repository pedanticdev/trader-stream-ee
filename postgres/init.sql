CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Audit log table for security events
CREATE TABLE audit_log
(
    id                 VARCHAR(36) PRIMARY KEY,
    timestamp          TIMESTAMP   NOT NULL,
    username           VARCHAR(100),
    ip_address         VARCHAR(45),
    action             VARCHAR(100) NOT NULL,
    resource           VARCHAR(255),
    method_name        VARCHAR(255),
    class_name         VARCHAR(255),
    sensitivity_level  VARCHAR(20),
    success            BOOLEAN     NOT NULL,
    duration_ms        BIGINT,
    error_message      VARCHAR(1000),
    user_agent         VARCHAR(500),
    details            TEXT
);

-- Indexes for audit log queries
CREATE INDEX idx_audit_username ON audit_log (username);
CREATE INDEX idx_audit_timestamp ON audit_log (timestamp);
CREATE INDEX idx_audit_action ON audit_log (action);
CREATE INDEX idx_audit_level ON audit_log (sensitivity_level);

-- Patient table
CREATE TABLE patient
(
    id                 UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    first_name         VARCHAR(50) NOT NULL,
    last_name          VARCHAR(50) NOT NULL,
    date_of_birth      DATE        NOT NULL,
    ssn                VARCHAR(255),
    email              VARCHAR(255),
    phone              VARCHAR(255),
    address            VARCHAR(255),
    blood_type         VARCHAR(10),
    allergies          VARCHAR(500),
    medical_conditions VARCHAR(1000),
    assigned_doctor    VARCHAR(100),
    department         VARCHAR(50),
    created_at         TIMESTAMP,
    updated_at         TIMESTAMP
);

INSERT INTO patient (id, first_name, last_name, date_of_birth, ssn, email, phone, address, blood_type, allergies,
                     medical_conditions, assigned_doctor, department, created_at, updated_at)
VALUES ('a8b8b4f0-3e5b-48b0-8b4a-0e1b6d4b0b1b', 'Jane', 'Doe', '1985-05-15', '123-45-6789', 'jane.doe@email.com',
        '555-0101', '123 Main St, Springfield', 'O+', 'Penicillin', 'Hypertension', 'Dr. Smith', 'Cardiology', NOW(),
        NOW()),
       ('f8b8b4f0-3e5b-48b0-8b4a-0e1b6d4b0b1c', 'John', 'Smith', '1978-03-22', '987-65-4321', 'john.smith@email.com',
        '555-0102', '456 Oak Ave, Springfield', 'A+', 'None', 'Diabetes Type 2', 'Dr. Smith', 'Cardiology', NOW(),
        NOW()),
       ('e8b8b4f0-3e5b-48b0-8b4a-0e1b6d4b0b1d', 'Alice', 'Johnson', '1992-11-08', '456-78-9012', 'alice.j@email.com',
        '555-0103', '789 Pine Rd, Springfield', 'B-', 'Latex', 'Asthma', 'Dr. Williams', 'Emergency', NOW(), NOW()),
       ('d8b8b4f0-3e5b-48b0-8b4a-0e1b6d4b0b1e', 'Robert', 'Brown', '1965-07-30', '321-54-9876', 'rbrown@email.com',
        '555-0104', '321 Elm St, Springfield', 'AB+', 'Shellfish', 'Coronary artery disease', 'Dr. Smith', 'Cardiology',
        NOW(), NOW()),
       ('c8b8b4f0-3e5b-48b0-8b4a-0e1b6d4b0b1f', 'Emma', 'Davis', '1988-09-14', '654-32-1098', 'emma.d@email.com',
        '555-0105', '555 Maple Ln, Springfield', 'O-', 'None', 'None', 'Dr. Williams', 'Emergency', NOW(), NOW());
