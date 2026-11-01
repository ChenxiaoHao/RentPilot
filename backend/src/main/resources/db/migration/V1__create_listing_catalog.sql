CREATE TABLE dataset_import (
    dataset_id VARCHAR(80) PRIMARY KEY,
    source_name VARCHAR(200) NOT NULL,
    source_sha256 VARCHAR(64) NOT NULL,
    row_count INTEGER NOT NULL CHECK (row_count > 0),
    imported_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE listing (
    id VARCHAR(40) PRIMARY KEY,
    dataset_id VARCHAR(80) NOT NULL REFERENCES dataset_import(dataset_id),
    name VARCHAR(300) NOT NULL,
    district VARCHAR(40) NOT NULL,
    town VARCHAR(80) NOT NULL,
    sector VARCHAR(100) NOT NULL,
    community VARCHAR(200) NOT NULL,
    address VARCHAR(500) NOT NULL,
    longitude NUMERIC(10,6),
    latitude NUMERIC(10,6),
    rental_type VARCHAR(10) NOT NULL CHECK (rental_type IN ('ENTIRE', 'SHARED')),
    layout VARCHAR(100) NOT NULL,
    bedrooms INTEGER NOT NULL CHECK (bedrooms BETWEEN 1 AND 10),
    rent NUMERIC(12,2) NOT NULL CHECK (rent > 0),
    area NUMERIC(10,2) NOT NULL CHECK (area > 0),
    floor_description VARCHAR(80) NOT NULL,
    orientation VARCHAR(40) NOT NULL,
    decoration VARCHAR(80) NOT NULL,
    nearest_metro VARCHAR(100) NOT NULL,
    metro_lines_raw VARCHAR(120) NOT NULL,
    metro_line_tokens VARCHAR(300) NOT NULL,
    metro_distance_meters INTEGER CHECK (metro_distance_meters >= 0),
    metro_walk_minutes INTEGER CHECK (metro_walk_minutes >= 0),
    private_bathroom VARCHAR(7) NOT NULL CHECK (private_bathroom IN ('YES','NO','UNKNOWN')),
    private_kitchen VARCHAR(7) NOT NULL CHECK (private_kitchen IN ('YES','NO','UNKNOWN')),
    gas VARCHAR(7) NOT NULL CHECK (gas IN ('YES','NO','UNKNOWN')),
    balcony VARCHAR(7) NOT NULL CHECK (balcony IN ('YES','NO','UNKNOWN')),
    air_conditioning VARCHAR(7) NOT NULL CHECK (air_conditioning IN ('YES','NO','UNKNOWN')),
    refrigerator VARCHAR(7) NOT NULL CHECK (refrigerator IN ('YES','NO','UNKNOWN')),
    washing_machine VARCHAR(7) NOT NULL CHECK (washing_machine IN ('YES','NO','UNKNOWN')),
    water_heater VARCHAR(7) NOT NULL CHECK (water_heater IN ('YES','NO','UNKNOWN')),
    elevator VARCHAR(7) NOT NULL CHECK (elevator IN ('YES','NO','UNKNOWN')),
    furnished VARCHAR(7) NOT NULL CHECK (furnished IN ('YES','NO','UNKNOWN')),
    pets_allowed VARCHAR(7) NOT NULL CHECK (pets_allowed IN ('YES','NO','UNKNOWN')),
    cooking_allowed VARCHAR(7) NOT NULL CHECK (cooking_allowed IN ('YES','NO','UNKNOWN')),
    deposit NUMERIC(12,2) NOT NULL CHECK (deposit >= 0),
    payment_method VARCHAR(80) NOT NULL,
    property_fee NUMERIC(12,2) CHECK (property_fee >= 0),
    availability VARCHAR(100) NOT NULL,
    description TEXT NOT NULL,
    data_nature VARCHAR(100) NOT NULL,
    location_status VARCHAR(30) NOT NULL CHECK (location_status IN ('COMMUNITY_REFERENCE','UNVERIFIED')),
    CHECK ((longitude IS NULL AND latitude IS NULL) OR
           (longitude BETWEEN 120 AND 123 AND latitude BETWEEN 30 AND 32.5)),
    CHECK ((metro_distance_meters IS NULL AND metro_walk_minutes IS NULL) OR
           (metro_distance_meters IS NOT NULL AND metro_walk_minutes IS NOT NULL)),
    CHECK ((location_status = 'UNVERIFIED' AND longitude IS NULL AND latitude IS NULL) OR
           (location_status = 'COMMUNITY_REFERENCE' AND longitude IS NOT NULL AND latitude IS NOT NULL))
);

CREATE INDEX idx_listing_district_town ON listing(district, town);
CREATE INDEX idx_listing_rental_bedrooms_rent ON listing(rental_type, bedrooms, rent, id);
CREATE INDEX idx_listing_rent ON listing(rent, id);
CREATE INDEX idx_listing_metro_walk ON listing(metro_walk_minutes, id);
