# Syezw App + Backend System Overview

This document describes the current app features, local Room database design, backend service, sync/encryption flow, image handling, and how data moves between the Android app and the backend PostgreSQL database.

## 1) App Features

### Diary
- Create, edit, delete diary entries.
- Each entry supports:
  - `content` (text)
  - `author`
  - `tags`
  - `location` (optional)
  - `timestamp` (when the entry is for)
  - `updatedAt` (last update time)
  - `uuid` (stable identifier for sync)
  - images (stored as file names)
- Import/export JSON for diaries.
- Search and filter by author/tags/keywords.
- In detail view, missing images are fetched from backend automatically.

### TODO
- Create, edit, delete tasks.
- Mark completed/uncompleted.
- Order:
  - Uncompleted tasks first
  - Completed tasks ordered by completion time (desc)
- Copy a task to clipboard.
- Import/export JSON for tasks.
- Search by task name.

### Period Tracking
- Enable/disable period tracking.
- Records include start date, end date, notes, updatedAt.
- Import/export via settings.

### Love Screen
- Optional background image.
- Toggle background image on/off.

### Settings / Utilities
- Default author and together date.
- Remote sync configuration:
  - API base URL
  - API key (for backend authorization)
  - AES passphrase
- Sync actions:
  - Upload
  - Download
  - Export Sync Logs (to Downloads)
- Local utilities:
  - Check unused diary images under Downloads/syezw_diary_images
  - Backup/export and restore/import (WorkManager + file pickers)
- Sync logs stored locally and exportable.
- Upload/download rate limit: at least 30 seconds between two successful attempts (client side). If the last attempt failed, the next attempt is not throttled.
- Upload/download progress bar with percent and a last-summary line below the bar.

## 2) Local Database (Android Room)

Room database: `syezw_database`

Entities:
- `Diary` (table `diary_list`)
- `TodoTask` (table `todo_list`)
- `PeriodRecord` (table `period_records`)

Important fields:
- **Diary**
  - `uuid`: stable id for sync
  - `updatedAt`: local last-update timestamp
  - `imageUris`: list of file names
- **TodoTask**
  - `uuid`, `updatedAt`, `completedAt`, `createdAt`
- **PeriodRecord**
  - `startDate`, `endDate`, `updatedAt`

Migration:
- DB version 3 adds `uuid` and `updatedAt` to Diary/Todo and `updatedAt` to Period.
- Existing rows are backfilled.

Room is the **source of truth** on the device. The backend stores encrypted payloads and image blobs.

## 3) Backend Service

### Overview
- Rust + Actix Web.
- Reads DB config from `.env` (backend controlled).
- Creates a global `PgPool` on startup and reuses it for all requests.
- No server-side decryption; encrypted payloads are stored and returned as-is.

### Environment Variables
Backend (`backend/.env`):
- `BIND_ADDR`
- `PG_HOST`
- `PG_PORT`
- `PG_DB`
- `PG_USER`
- `PG_PASSWORD`
- `API_KEY` (required if set; clients must send `X-API-Key`)

Tests (`backend/.env`):
- `TEST_PG_DB`

### Endpoints
- `POST /sync/meta`
  - Returns server-side metadata (uuid + updatedAt) for diary/todo/period.
  - Used by clients to determine which records need upload.
- `POST /sync/upload`
  - Upload encrypted Diary/Todo/Period payloads.
  - Also supports image uploads (legacy path).
- `POST /sync/download`
  - Accepts client metadata and returns only server records that are missing or outdated on the client.
  - Returns image blobs linked via diary refs.
- `POST /images/hashes`
  - Return all stored image hashes.
- `POST /images/upload`
  - Upload encrypted image blobs by hash.
- `POST /images/refs`
  - Return all diary image refs.
- `POST /images/refs/upsert`
  - Upsert diary image refs (diary_uuid + file_name → hash).
- `POST /images/fetch`
  - Fetch one image blob by diary_uuid + file_name.

## 4) Backend Database Schema (PostgreSQL)

Tables (see `backend/sql/schema.sql`):
- `diary_sync`
  - `uuid` PK
  - `author`, `timestamp`, `updated_at`
  - `payload_iv`, `payload_data` (AES-GCM encrypted JSON)
- `todo_sync`
  - `uuid` PK
  - `author`, `is_completed`, `created_at`, `completed_at`, `updated_at`
  - `payload_iv`, `payload_data`
- `period_sync`
  - `start_date` PK, `end_date`
  - `updated_at`, `payload_iv`, `payload_data`
- `diary_images`
  - `hash` PK
  - `blob_iv`, `blob_data`, `updated_at`
- `diary_image_refs`
  - `(diary_uuid, file_name)` PK
  - `hash`, `updated_at`
  - index on `hash`

Notes:
- Textual content is stored encrypted in `payload_data`.
- Images are stored encrypted in `diary_images`.
- `diary_image_refs` maps a diary entry to a file name and a hash.

## 5) Encryption

Client-side only:
- AES-GCM (128-bit) with IV.
- Key derived from passphrase via MD5 (16 bytes).
- Encrypted payloads are JSON serialized, encrypted, and stored in DB.
- Backend never decrypts.

Image encryption:
- Images are encrypted client-side and stored as `blob_data` + `blob_iv`.
- Hash is SHA-256 of the plain image bytes.

## 6) Sync Behavior (App ↔ Backend)

### Upload
1. Validate API base URL, API key, and AES passphrase.
2. Encrypt:
   - Diary payload: content/tags/location/imageUris
   - Todo payload: name
   - Period payload: notes
3. `POST /sync/meta` to get server-side metadata (uuid + updatedAt).
4. Compare local `updatedAt` with server metadata:
   - Upload only records that are missing on server or newer than server.
5. `POST /sync/upload` in size-based batches.
4. Images:
   - `POST /images/hashes` to get server hashes.
   - Upload only missing hashes via `POST /images/upload`.
   - Always upsert diary → file name → hash refs via `POST /images/refs/upsert`.
5. Logs:
   - Upload start/end, failure reason, and counts.
6. Upload rate limit:
   - Minimum 30s between two uploads (client side).

### Download
1. Validate API base URL, API key, and AES passphrase.
2. Collect local metadata (uuid + updatedAt) and call `POST /sync/download`.
3. Server returns only records that are missing or outdated on the client.
3. Decrypt and upsert in Room:
   - If local is missing → insert.
   - If remote `updatedAt` is newer → update.
4. Images:
   - `POST /images/refs` to get all refs.
   - For each ref, compare local hash (if file exists).
   - Missing or different hash → fetch with `POST /images/fetch`.
5. Logs:
   - Download start/end, failure reason, and counts.

### Diary Detail Image Fetch
- When a diary detail page is opened:
  - For each image, if local file missing → fetch from backend.
  - Each image download success/failure is logged with the diary title prefix.

## 7) Relationship Between Room and Backend DB

- Room is the local, authoritative store for the app UI.
- Backend DB stores encrypted data for backup/sync.
- Sync is **pull & push**:
  - Upload uses local Room data as source.
  - Download merges by `uuid` / `updatedAt`.
- Backend does **not** resolve conflicts; merge logic lives in the app:
  - Newer `updatedAt` wins for each row.
  - Same UUID but older remote record will not overwrite local.

## 8) UI Overview

### Diary Screen
- List with filters, search, and add/edit.
- Detail dialog shows entry + images.
- Missing images auto-download.

### TODO Screen
- List with search.
- Completed tasks sorted after incomplete.
- Copy task to clipboard.
- Import/export tasks.

### Period Screen
- Show period history and record notes.

### Love Screen
- Optional background image.

### Settings Screen
- Default author, together date.
- Period tracking enable/disable.
- Remote sync settings and actions.
- Export sync logs.
- Check unused diary images.

## 9) Logging / Diagnostics

App:
- Sync logs stored in DataStore.
- Export to Downloads as text file.
- Each entry includes time, action, success/fail, and message.
- Image downloads log diary title prefix + file name.

Backend:
- Actix middleware logs requests.
- SQLx can emit slow query logs when statements exceed threshold.

## 10) Tests

Backend:
- `backend/tests/sync_tests.rs`
  - `upload_then_download_round_trip`
  - `upload_with_image_hash_dedup_and_fetch`
- Uses `TEST_PG_*` environment variables.

Android:
- Unit tests for app components are in place where applicable.
- Most sync logic is exercised at runtime via manual testing.

## 11) Data Flow Summary

1. **User creates/edits data** → Room DB.
2. **Upload** → Room → encrypted payloads → backend DB.
3. **Download** → backend DB → encrypted payloads → decrypted → Room.
4. **Images**:
   - Upload: de-duplicate via hash.
   - Download: compare hash and fetch missing images.

## 12) Known Limitations / Notes

- Backend returns only records that are missing or outdated on `/sync/download` (metadata-based incremental sync).
- If the same entry is edited on multiple devices, app uses `updatedAt` to resolve conflicts.
- Image fetch is per diary/image, and download is on-demand when missing.
