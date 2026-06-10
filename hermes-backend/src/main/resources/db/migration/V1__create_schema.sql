CREATE TABLE IF NOT EXISTS listings (
    id                   UUID                     NOT NULL,
    funda_id             VARCHAR(255)             NOT NULL,
    url                  VARCHAR(255)             NOT NULL,
    street               VARCHAR(255),
    house_number         VARCHAR(255),
    house_number_addition VARCHAR(255),
    zip_code             VARCHAR(255),
    city                 VARCHAR(255),
    province             VARCHAR(255),
    first_seen_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    last_seen_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_listings_funda_id UNIQUE (funda_id)
);

CREATE TABLE IF NOT EXISTS listing_snapshots (
    id                   UUID                     NOT NULL,
    listing_id           UUID                     NOT NULL,
    scraped_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    asking_price         INTEGER,
    living_area_m2       INTEGER,
    rooms                INTEGER,
    energy_label         VARCHAR(255),
    listed_on_funda_since DATE,
    status               VARCHAR(50),
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS listing_summaries (
    id           UUID                     NOT NULL,
    listing_id   UUID                     NOT NULL,
    summary      TEXT                     NOT NULL,
    generated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_listing_summaries_listing_id UNIQUE (listing_id)
);

CREATE TABLE IF NOT EXISTS scraping_sessions (
    id                 UUID                     NOT NULL,
    status             VARCHAR(50)              NOT NULL,
    type               VARCHAR(50)              NOT NULL,
    city               VARCHAR(255),
    min_price          INTEGER,
    max_price          INTEGER,
    min_area           INTEGER,
    max_area           INTEGER,
    page_limit         INTEGER                  NOT NULL,
    funda_url          VARCHAR(255)             NOT NULL,
    target_listing_url VARCHAR(255),
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    started_at         TIMESTAMP WITH TIME ZONE,
    completed_at       TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (id)
);

-- Spring Modulith event publication table — text columns are TEXT from the start
CREATE TABLE IF NOT EXISTS event_publication (
    id                      UUID                     NOT NULL,
    listener_id             TEXT                     NOT NULL,
    event_type              TEXT                     NOT NULL,
    serialized_event        TEXT                     NOT NULL,
    publication_date        TIMESTAMP WITH TIME ZONE NOT NULL,
    status                  VARCHAR(50),
    completion_date         TIMESTAMP WITH TIME ZONE,
    last_resubmission_date  TIMESTAMP WITH TIME ZONE,
    completion_attempts     INTEGER,
    PRIMARY KEY (id)
);
