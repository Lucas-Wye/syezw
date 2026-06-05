CREATE TABLE IF NOT EXISTS diary_sync (
    uuid TEXT PRIMARY KEY,
    author TEXT NOT NULL,
    timestamp BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    payload_iv TEXT NOT NULL,
    payload_data TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS todo_sync (
    uuid TEXT PRIMARY KEY,
    author TEXT NOT NULL,
    is_completed BOOLEAN NOT NULL,
    created_at BIGINT NOT NULL,
    completed_at BIGINT NULL,
    updated_at BIGINT NOT NULL,
    payload_iv TEXT NOT NULL,
    payload_data TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS period_sync (
    start_date DATE PRIMARY KEY,
    end_date DATE NOT NULL,
    updated_at BIGINT NOT NULL,
    payload_iv TEXT NOT NULL,
    payload_data TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS diary_images (
    hash TEXT PRIMARY KEY,
    blob_iv TEXT NOT NULL,
    blob_data TEXT NOT NULL,
    updated_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS diary_image_refs (
    diary_uuid TEXT NOT NULL,
    file_name TEXT NOT NULL,
    hash TEXT NOT NULL,
    updated_at BIGINT NOT NULL,
    PRIMARY KEY (diary_uuid, file_name)
);

CREATE INDEX IF NOT EXISTS idx_diary_image_refs_hash ON diary_image_refs(hash);
