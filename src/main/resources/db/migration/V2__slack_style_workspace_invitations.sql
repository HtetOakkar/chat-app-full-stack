-- Migration V2: Add custom welcome message and initial pre-assigned channel IDs to workspace invitations
ALTER TABLE workspace_invitations ADD COLUMN custom_message VARCHAR(1000) NULL;
ALTER TABLE workspace_invitations ADD COLUMN initial_channel_ids VARCHAR(1000) NULL;
