ALTER TABLE messages ADD COLUMN call_outcome VARCHAR(50) NULL;
ALTER TABLE messages ADD COLUMN call_duration INT NULL;
ALTER TABLE messages ADD COLUMN video_used BOOLEAN NULL;
