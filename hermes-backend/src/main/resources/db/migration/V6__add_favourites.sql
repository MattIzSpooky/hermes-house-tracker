CREATE TABLE favourites (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id   UUID        NOT NULL,
    listing_id  UUID        NOT NULL REFERENCES listings(id) ON DELETE CASCADE,
    saved_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_favourites_client_listing UNIQUE (client_id, listing_id)
);

CREATE INDEX idx_favourites_client_id ON favourites(client_id);
