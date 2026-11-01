CREATE TABLE member_account (
    id VARCHAR(36) PRIMARY KEY,
    username VARCHAR(24) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    nickname VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE member_session (
    token_hash VARCHAR(64) PRIMARY KEY,
    member_id VARCHAR(36) NOT NULL REFERENCES member_account(id) ON DELETE CASCADE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_session_expiry ON member_session(expires_at);
CREATE TABLE favorite_listing (
    member_id VARCHAR(36) NOT NULL REFERENCES member_account(id) ON DELETE CASCADE,
    listing_id VARCHAR(40) NOT NULL REFERENCES listing(id),
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(member_id,listing_id)
);
CREATE TABLE browsing_history (
    member_id VARCHAR(36) NOT NULL REFERENCES member_account(id) ON DELETE CASCADE,
    listing_id VARCHAR(40) NOT NULL REFERENCES listing(id),
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(member_id,listing_id)
);
CREATE TABLE viewing_plan (
    id VARCHAR(36) PRIMARY KEY,
    member_id VARCHAR(36) NOT NULL REFERENCES member_account(id) ON DELETE CASCADE,
    listing_id VARCHAR(40) NOT NULL REFERENCES listing(id),
    planned_date DATE NOT NULL,
    slot VARCHAR(15) NOT NULL CHECK(slot IN ('MORNING','AFTERNOON','EVENING')),
    status VARCHAR(15) NOT NULL CHECK(status IN ('PLANNED','CANCELLED','COMPLETED')),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_viewing_member ON viewing_plan(member_id,status,planned_date);
