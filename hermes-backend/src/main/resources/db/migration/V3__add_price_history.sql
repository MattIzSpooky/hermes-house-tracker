-- Add new columns to listings
ALTER TABLE listings ADD COLUMN status VARCHAR(50);
ALTER TABLE listings ADD COLUMN last_updated_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE listings ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE;

-- New price history table
CREATE TABLE price_history_entries (
    id          UUID                     NOT NULL,
    listing_id  UUID                     NOT NULL,
    price       INTEGER,
    status      VARCHAR(50),
    source      VARCHAR(255),
    date        DATE,
    timestamp   TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (id),
    CONSTRAINT uk_price_history_listing_timestamp UNIQUE (listing_id, timestamp),
    CONSTRAINT fk_price_history_listing FOREIGN KEY (listing_id)
        REFERENCES listings (id) ON DELETE CASCADE
);

-- Drop old snapshots table
DROP TABLE listing_snapshots;
