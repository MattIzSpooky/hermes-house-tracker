-- Replace PostGIS geometry columns on cities with plain lat/lon doubles.
-- The geometry was only used to cache the city centroid for radius lookups;
-- ST_SetSRID(ST_MakePoint(lon, lat), 4326) at query time is cheaper and avoids
-- the Hibernate JTS serialization issue entirely.

ALTER TABLE cities ADD COLUMN IF NOT EXISTS latitude  DOUBLE PRECISION;
ALTER TABLE cities ADD COLUMN IF NOT EXISTS longitude DOUBLE PRECISION;

-- Migrate any existing rows (table is likely empty on first deploy)
UPDATE cities
   SET latitude  = ST_Y(location::geometry),
       longitude = ST_X(location::geometry)
 WHERE location IS NOT NULL;

ALTER TABLE cities ALTER COLUMN latitude  SET NOT NULL;
ALTER TABLE cities ALTER COLUMN longitude SET NOT NULL;

DROP INDEX   IF EXISTS idx_cities_location;
ALTER TABLE  cities DROP COLUMN IF EXISTS location;
ALTER TABLE  cities DROP COLUMN IF EXISTS bounding_box;
