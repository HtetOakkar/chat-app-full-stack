CREATE TABLE IF NOT EXISTS contacts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    contact_id BIGINT NOT NULL,
    created_at TIMESTAMP NULL,
    CONSTRAINT uk_contacts_owner_contact UNIQUE (owner_id, contact_id),
    CONSTRAINT fk_contacts_owner FOREIGN KEY (owner_id) REFERENCES users(id),
    CONSTRAINT fk_contacts_contact FOREIGN KEY (contact_id) REFERENCES users(id)
);

CREATE INDEX idx_contacts_owner_id ON contacts(owner_id);
