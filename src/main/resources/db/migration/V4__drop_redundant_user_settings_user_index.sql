-- Migration V4: Drop the user_settings index that duplicates the unique constraint's index.
-- uq_user_settings_user_id already indexes user_id uniquely and serves fk_user_settings_user,
-- so idx_user_settings_user_id only costs writes and storage.
DROP INDEX idx_user_settings_user_id ON user_settings;
