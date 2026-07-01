ALTER TABLE favourites RENAME TO favorites;
ALTER TABLE favorites RENAME CONSTRAINT uq_favourites_client_listing TO uq_favorites_client_listing;
ALTER INDEX idx_favourites_client_id RENAME TO idx_favorites_client_id;
