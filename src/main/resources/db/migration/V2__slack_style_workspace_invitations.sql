ALTER TABLE workspace_invitations ADD COLUMN IF NOT EXISTS custom_message VARCHAR(1000) NULL;
ALTER TABLE workspace_invitations ADD COLUMN IF NOT EXISTS initial_channel_ids VARCHAR(1000) NULL;
