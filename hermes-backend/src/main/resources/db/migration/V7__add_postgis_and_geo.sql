CREATE EXTENSION IF NOT EXISTS postgis;

ALTER TABLE listings ADD COLUMN IF NOT EXISTS location     geometry(Point,   4326);
ALTER TABLE listings ADD COLUMN IF NOT EXISTS bounding_box geometry(Polygon, 4326);

CREATE INDEX IF NOT EXISTS idx_listings_location ON listings USING gist(location);

CREATE TABLE IF NOT EXISTS cities (
    id           UUID                     NOT NULL,
    name         VARCHAR(255)             NOT NULL,
    location     geometry(Point, 4326)    NOT NULL,
    bounding_box geometry(Polygon, 4326),
    fetched_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_cities_name UNIQUE (name)
);

CREATE INDEX IF NOT EXISTS idx_cities_location ON cities USING gist(location);
