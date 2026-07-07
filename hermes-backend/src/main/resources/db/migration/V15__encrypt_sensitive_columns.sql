TRUNCATE TABLE chat_messages;
TRUNCATE TABLE agent_tasks, notifications;
TRUNCATE TABLE user_profiles;

ALTER TABLE notifications ALTER COLUMN title TYPE TEXT;

ALTER TABLE user_profiles ALTER COLUMN street TYPE TEXT;
ALTER TABLE user_profiles ALTER COLUMN house_number TYPE TEXT;
ALTER TABLE user_profiles ALTER COLUMN house_number_addition TYPE TEXT;
ALTER TABLE user_profiles ALTER COLUMN zip_code TYPE TEXT;
ALTER TABLE user_profiles ALTER COLUMN city TYPE TEXT;
ALTER TABLE user_profiles ALTER COLUMN province TYPE TEXT;
ALTER TABLE user_profiles ALTER COLUMN email TYPE TEXT;
ALTER TABLE user_profiles ALTER COLUMN latitude TYPE TEXT USING latitude::text;
ALTER TABLE user_profiles ALTER COLUMN longitude TYPE TEXT USING longitude::text;

ALTER TABLE chat_messages ADD COLUMN encryption_key_version INT NOT NULL DEFAULT 1;
ALTER TABLE notifications ADD COLUMN encryption_key_version INT NOT NULL DEFAULT 1;
ALTER TABLE user_profiles ADD COLUMN encryption_key_version INT NOT NULL DEFAULT 1;
ALTER TABLE agent_tasks ADD COLUMN encryption_key_version INT NOT NULL DEFAULT 1;
