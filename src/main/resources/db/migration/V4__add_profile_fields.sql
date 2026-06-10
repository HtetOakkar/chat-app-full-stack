ALTER TABLE users 
ADD COLUMN email VARCHAR(255) NULL,
ADD COLUMN full_name VARCHAR(100) NULL,
ADD COLUMN birth_date DATE NULL,
ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE,
ADD COLUMN email_verification_code VARCHAR(6) NULL,
ADD COLUMN email_verification_expires_at TIMESTAMP NULL,
ADD COLUMN verification_attempts INT NOT NULL DEFAULT 0,
ADD COLUMN last_code_requested_at TIMESTAMP NULL;

ALTER TABLE users ADD CONSTRAINT uk_users_email UNIQUE (email);
