-- Widen any event_publication text columns that were previously created as VARCHAR(255)
-- by Hibernate's ddl-auto=update. No-op for fresh installations (V1 already uses TEXT).
DO $$
DECLARE
    col RECORD;
BEGIN
    FOR col IN
        SELECT column_name
        FROM information_schema.columns
        WHERE table_name = 'event_publication'
          AND column_name IN ('serialized_event', 'event_type', 'listener_id')
          AND data_type = 'character varying'
    LOOP
        EXECUTE 'ALTER TABLE event_publication ALTER COLUMN ' || col.column_name || ' TYPE TEXT';
    END LOOP;
END $$;
