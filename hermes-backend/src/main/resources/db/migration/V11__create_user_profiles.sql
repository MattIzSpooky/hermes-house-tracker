CREATE TABLE user_profiles (
    user_id               UUID PRIMARY KEY,
    street                VARCHAR(255),
    house_number          VARCHAR(50),
    house_number_addition VARCHAR(50),
    zip_code              VARCHAR(20),
    city                  VARCHAR(255),
    province              VARCHAR(255),
    latitude              DOUBLE PRECISION,
    longitude             DOUBLE PRECISION,
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL
);
