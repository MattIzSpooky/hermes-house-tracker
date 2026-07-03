TRUNCATE TABLE chat_messages;

ALTER TABLE chat_messages ADD COLUMN user_id UUID NOT NULL;
CREATE INDEX idx_chat_messages_user_id ON chat_messages(user_id);
