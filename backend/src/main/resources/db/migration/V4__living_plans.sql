CREATE TABLE living_plan (
    id VARCHAR(36) PRIMARY KEY,
    member_id VARCHAR(36) NOT NULL REFERENCES member_account(id) ON DELETE CASCADE,
    title VARCHAR(60) NOT NULL,
    active_version_id VARCHAR(36),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_living_plan_member ON living_plan(member_id,updated_at DESC);

CREATE TABLE living_plan_version (
    id VARCHAR(36) PRIMARY KEY,
    plan_id VARCHAR(36) NOT NULL REFERENCES living_plan(id) ON DELETE CASCADE,
    version_no INTEGER NOT NULL,
    snapshot_json TEXT NOT NULL,
    primary_listing_id VARCHAR(40) NOT NULL,
    primary_home_name VARCHAR(120) NOT NULL,
    primary_district VARCHAR(40) NOT NULL,
    primary_town VARCHAR(40) NOT NULL,
    primary_rent NUMERIC(10,2) NOT NULL,
    primary_commute_minutes INTEGER,
    home_count INTEGER NOT NULL,
    coverage VARCHAR(10) NOT NULL CHECK(coverage IN ('COMPLETE','PARTIAL')),
    captured_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(plan_id,version_no)
);
CREATE INDEX idx_living_plan_version_plan ON living_plan_version(plan_id,version_no DESC);