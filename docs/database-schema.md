# Database schema

## Database and migration safety

The application targets the configured Neon PostgreSQL database through `NEON_DATABASE_URL`, `NEON_DATABASE_USERNAME`, and `NEON_DATABASE_PASSWORD`. The JDBC URL must retain `sslmode=require`. Flyway uses the same Spring datasource. `FLYWAY_ENABLED` defaults to `false`, and Hibernate uses `ddl-auto=validate`; Hibernate will not create, update, or delete schema objects.

Migrations V1–V3 create the core schema and policy extraction tables. `V4__add_policy_chunk_vectors.sql` requests pgvector, adds a nullable `vector(1536)` column, adds `INDEXED`, and creates an HNSW cosine index. `V5__make_application_document_uploads_idempotent.sql` adds a nullable SHA-256 checksum and partial uniqueness index for duplicate uploads. V4/V5 are generated only and have not been applied to Neon. No Neon schema mutation was performed.

Before applying migrations, verify the exact Neon project, branch, database, role, and migration history. For V4, confirm pgvector is available on that target and inspect `pg_extension` read-only before enabling it. Review all SQL and a recovery point. Do not blindly apply V1–V5 to a database that already contains tables or untracked schema. For a confirmed new demo branch only, review the pending migrations and deliberately start with `FLYWAY_ENABLED=true`; then turn it back off. Routine app startup never applies migrations. No migration has been run against Neon in this task.

## pgvector and embeddings

pgvector availability/enabled state on the configured Neon target is unverified. V4 contains `CREATE EXTENSION IF NOT EXISTS vector`; it must not be applied until a human confirms the intended branch and extension prerequisite. The selected provider model defaults to `text-embedding-3-small` with 1536 dimensions. Runtime config rejects a different dimension to avoid silent vector mismatch.

## Tables

UUID primary keys are assigned by Hibernate. Event timestamps use `TIMESTAMPTZ` and Java `Instant`; effective dates use PostgreSQL `DATE` and Java `LocalDate`. Money and rates use PostgreSQL `NUMERIC` and Java `BigDecimal`. Foreign keys use `ON DELETE RESTRICT`; no application, status-history, document, or audit rows are removed through cascading deletes.

### `users`

Minimal customer profile. `external_reference` is nullable and uniquely indexed only when present. Email and phone are nullable and not unique because account identity and authentication policy are outside this phase.

| Column | Type | Constraints / relationship |
|---|---|---|
| `id` | UUID | Primary key |
| `external_reference` | VARCHAR(160) | Nullable; partial unique index |
| `full_name` | VARCHAR(200) | Required, nonblank |
| `email` | VARCHAR(320) | Nullable |
| `phone` | VARCHAR(32) | Nullable |
| `created_at`, `updated_at` | TIMESTAMPTZ | Required |

### `loan_products`

Lender-supplied catalogue entries. No lender products or market rates are seeded.

| Column | Type | Constraints / relationship |
|---|---|---|
| `id` | UUID | Primary key |
| `product_code` | VARCHAR(64) | Required, unique, nonblank |
| `product_name` | VARCHAR(200) | Required, nonblank |
| `loan_type` | VARCHAR(48) | Required, nonblank; open string for future product types |
| `description` | VARCHAR(4000) | Nullable |
| `minimum_amount`, `maximum_amount` | NUMERIC(19,2) | Nonnegative; maximum >= minimum |
| `minimum_annual_interest_rate`, `maximum_annual_interest_rate` | NUMERIC(7,4) | 0–100; maximum >= minimum |
| `minimum_tenure_months`, `maximum_tenure_months` | INTEGER | 1–600; maximum >= minimum |
| `minimum_monthly_income` | NUMERIC(19,2) | Nullable; nonnegative |
| `active` | BOOLEAN | Required, defaults true |
| `created_at`, `updated_at` | TIMESTAMPTZ | Required |

Indexes: unique `product_code`; `(active, loan_type)` for active catalogue filtering.

### `loan_applications`

Customer application record. `user_id` is required; `loan_product_id` is optional. `APPROVED` and `DECLINED` represent future recorded human outcomes, not AI recommendations; no decision workflow is implemented.

| Column | Type | Constraints / relationship |
|---|---|---|
| `id` | UUID | Primary key |
| `application_reference` | VARCHAR(48) | Required, unique |
| `user_id` | UUID | Required FK to `users`, delete restricted |
| `loan_product_id` | UUID | Nullable FK to `loan_products`, delete restricted |
| `requested_amount` | NUMERIC(19,2) | Required, positive |
| `requested_tenure_months` | INTEGER | Nullable, 1–600 |
| `monthly_income` | NUMERIC(19,2) | Nullable, nonnegative |
| `application_status` | VARCHAR(40) | Required status check |
| `created_at`, `updated_at` | TIMESTAMPTZ | Required |

Statuses: `DRAFT`, `SUBMITTED`, `UNDER_REVIEW`, `ADDITIONAL_DOCUMENTS_REQUIRED`, `DECISION_PENDING`, `APPROVED`, `DECLINED`, `WITHDRAWN`. Indexes: `(user_id, created_at DESC)`, optional product FK, `(application_status, created_at DESC)`.

### `application_documents`

Metadata for actual demo uploads. Binary files are stored separately under generated keys on a Docker volume; raw binaries are not stored in Neon.

| Column | Type | Constraints / relationship |
|---|---|---|
| `id` | UUID | Primary key |
| `application_id` | UUID | Required FK to `loan_applications`, delete restricted |
| `document_type` | VARCHAR(64) | Required |
| `storage_provider`, `storage_key` | VARCHAR(48), VARCHAR(512) | Nullable |
| `original_filename` | VARCHAR(255) | Required |
| `content_type` | VARCHAR(160) | Required |
| `file_size_bytes` | BIGINT | Required, nonnegative |
| `content_sha256` | VARCHAR(64) | Nullable for legacy rows; V5 checksum for new uploads |
| `document_status` | VARCHAR(24) | `REGISTERED`, `UPLOADED`, `PROCESSING`, `VERIFIED`, `REJECTED`, `FAILED` |
| `uploaded_at`, `created_at`, `updated_at` | TIMESTAMPTZ | Required |

Indexes: `(application_id, uploaded_at DESC)`, `document_status`, and V5 partial unique `(application_id, document_type, content_sha256)`. Repeated byte-identical uploads of the same type return the existing row. The generated-key file is stored on the local Docker volume and is not exposed by API responses.

### `policy_documents`

One row per named policy version. The unique index on `lower(document_name), version` prevents accidental duplicate versions without treating name case as a distinction. Legacy metadata registration sets `REGISTERED`; upload progresses through `UPLOADED`, `PROCESSING`, then `INDEXED`, `NEEDS_OCR`, or `FAILED`. `PROCESSED` represents page extraction before vector indexing. Existing `ACTIVE` and `ARCHIVED` statuses remain supported.

| Column | Type | Constraints / relationship |
|---|---|---|
| `id` | UUID | Primary key |
| `document_name` | VARCHAR(240) | Required |
| `document_type` | VARCHAR(64) | Required |
| `version` | VARCHAR(64) | Required |
| `effective_date` | DATE | Nullable |
| `status` | VARCHAR(24) | `REGISTERED`, `ACTIVE`, `ARCHIVED`, `UPLOADED`, `PROCESSING`, `PROCESSED`, `INDEXED`, `NEEDS_OCR`, `FAILED` |
| `storage_provider`, `storage_key` | VARCHAR(48), VARCHAR(512) | Nullable |
| `file_size_bytes` | BIGINT | Required, nonnegative; existing rows backfilled to 0 |
| `content_sha256` | VARCHAR(64) | Nullable lowercase SHA-256 hex |
| `processing_error` | VARCHAR(500) | Nullable sanitized processing message |
| `extraction_completed_at` | TIMESTAMPTZ | Nullable; set after extraction, including `NEEDS_OCR` |
| `created_at`, `updated_at` | TIMESTAMPTZ | Required |

Indexes: unique `(lower(document_name), version)`, `(document_type, status)`, and unique non-null `storage_key`.

### `policy_document_pages`

One row per source PDF page, including pages with no extractable text (empty string). This stores extracted page text, not a duplicate full-document body. Rows are retained with their policy version; retry replaces that document's page rows transactionally after a complete re-extraction.

| Column | Type | Constraints / relationship |
|---|---|---|
| `id` | UUID | Primary key |
| `policy_document_id` | UUID | Required FK to `policy_documents`, delete restricted |
| `page_number` | INTEGER | Required, one-based positive page number |
| `extracted_text` | TEXT | Required; can be empty for a textless page |
| `created_at` | TIMESTAMPTZ | Required |

Unique `(policy_document_id, page_number)` prevents duplicate page rows. The index supports ordered text preview by document/page.

### `policy_chunks`

Extracted text chunks with source page metadata. V4 adds a nullable pgvector embedding so historical rows remain valid until re-indexed.

| Column | Type | Constraints / relationship |
|---|---|---|
| `id` | UUID | Primary key |
| `policy_document_id` | UUID | Required FK to `policy_documents`, delete restricted |
| `chunk_index` | INTEGER | Required, nonnegative |
| `section_title` | VARCHAR(240) | Nullable |
| `chunk_text` | TEXT | Required, nonblank |
| `page_number` | INTEGER | Nullable, positive |
| `metadata` | JSONB | Required; contains page references and document version |
| `embedding` | `vector(1536)` | Added by V4; populated after provider calls succeed |
| `created_at` | TIMESTAMPTZ | Required |

Unique `(policy_document_id, chunk_index)` supports retry-safe document/chunk positions. HNSW uses cosine distance; retrieval is limited to `INDEXED` policy documents. Similarity is returned as `1 - cosine_distance`.

### `agent_checks`

Persisted deterministic demo check results: document presence, indicative affordability inputs, and unavailable compliance integrations. They do not represent verified identity, bureau data, sanctions checks, or a lending decision.

| Column | Type | Constraints / relationship |
|---|---|---|
| `id` | UUID | Primary key |
| `application_id` | UUID | Required FK to `loan_applications`, delete restricted |
| `agent_type` | VARCHAR(32) | `DOCUMENT_VERIFICATION`, `CREDIT_ASSESSMENT`, `COMPLIANCE` |
| `check_status` | VARCHAR(32) | `PENDING`, `PASS`, `FAIL`, `INCOMPLETE`, `NEEDS_HUMAN_REVIEW`, `ERROR` |
| `result_details` | JSONB | Required, defaults to `{}` |
| `requires_human_review` | BOOLEAN | Required, defaults true |
| `created_at` | TIMESTAMPTZ | Required |

Indexes: `(application_id, created_at DESC)` and `check_status`.

### `application_status_history`

Append-style status transitions. Initial transitions have a null previous status; `changed_by` remains nullable until identity and roles exist.

| Column | Type | Constraints / relationship |
|---|---|---|
| `id` | UUID | Primary key |
| `application_id` | UUID | Required FK to `loan_applications`, delete restricted |
| `previous_status` | VARCHAR(40) | Nullable, validated against status set |
| `new_status` | VARCHAR(40) | Required, validated against status set |
| `changed_by` | UUID | Nullable; no identity FK yet |
| `reason` | VARCHAR(1000) | Nullable |
| `created_at` | TIMESTAMPTZ | Required |

Index: `(application_id, created_at DESC)`.

### `audit_events`

Future system-action audit records. No sample events are inserted. Event details must not include credentials, tokens, or unnecessary document contents.

| Column | Type | Constraints / relationship |
|---|---|---|
| `id` | UUID | Primary key |
| `application_id` | UUID | Nullable FK to `loan_applications`, delete restricted |
| `actor_type` | VARCHAR(24) | `USER`, `SYSTEM`, `SERVICE` |
| `actor_reference` | VARCHAR(160) | Nullable |
| `event_type` | VARCHAR(120) | Required, nonblank |
| `event_details` | JSONB | Required, defaults to `{}` |
| `created_at` | TIMESTAMPTZ | Required |

Indexes: partial `(application_id, created_at DESC)` when associated with an application; `(event_type, created_at DESC)`.
