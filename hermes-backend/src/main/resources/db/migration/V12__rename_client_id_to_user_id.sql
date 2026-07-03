TRUNCATE TABLE notifications, agent_tasks, favorites CASCADE;

ALTER TABLE favorites RENAME COLUMN client_id TO user_id;
ALTER INDEX idx_favorites_client_id RENAME TO idx_favorites_user_id;
ALTER TABLE favorites RENAME CONSTRAINT uq_favorites_client_listing TO uq_favorites_user_listing;

ALTER TABLE notifications RENAME COLUMN client_id TO user_id;
ALTER INDEX idx_notifications_client_id_created RENAME TO idx_notifications_user_id_created;

ALTER TABLE agent_tasks RENAME COLUMN client_id TO user_id;
