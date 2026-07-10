CREATE INDEX idx_messages_public_recipient_sent_id
    ON messages(recipient_id, sent_at, id);

CREATE INDEX idx_messages_private_sender_recipient_sent_id
    ON messages(sender_id, recipient_id, sent_at, id);

CREATE INDEX idx_messages_private_recipient_sender_sent_id
    ON messages(recipient_id, sender_id, sent_at, id);

CREATE INDEX idx_messages_unread_sender_recipient_read_sent
    ON messages(sender_id, recipient_id, is_read, sent_at);
