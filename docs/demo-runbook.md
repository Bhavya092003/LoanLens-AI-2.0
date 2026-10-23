# Demo runbook

## Before starting

1. Copy `.env.example` to `.env` and set Neon credentials for the intended branch. Set `OPENAI_API_KEY` only if you want embeddings and policy Q&A.
2. In Neon Console, confirm the database/branch and inspect current schema plus `flyway_schema_history`. Run a read-only check for pgvector, for example `SELECT extversion FROM pg_extension WHERE extname='vector';`.
3. Review [V1](../backend/src/main/resources/db/migration/V1__create_core_lending_schema.sql), [V2](../backend/src/main/resources/db/migration/V2__allow_zero_minimum_loan_product_amount.sql), [V3](../backend/src/main/resources/db/migration/V3__add_policy_document_ingestion.sql), [V4](../backend/src/main/resources/db/migration/V4__add_policy_chunk_vectors.sql), and [V5](../backend/src/main/resources/db/migration/V5__make_application_document_uploads_idempotent.sql). Do not run V1–V5 against a populated schema without resolving exact pending migrations first. Verify an appropriate recovery point.
4. If the branch is confirmed new/empty and the reviewed migrations are appropriate, temporarily opt in to Flyway:

   ```powershell
   docker compose --env-file .env -f infra/docker-compose.yml run --rm -e FLYWAY_ENABLED=true backend
   ```

   Stop the app with Ctrl+C after migration/startup completes, then leave `FLYWAY_ENABLED=false`. Routine backend starts below will not apply migrations.

## Start and check services

From the project root:

```powershell
docker compose --env-file .env -f infra/docker-compose.yml up --build -d backend
docker compose --env-file .env -f infra/docker-compose.yml logs -f backend
```

Then run the React client in another terminal:

```powershell
Set-Location frontend
npm ci
npm run dev
```

Open `http://localhost:5173`. The backend health endpoint is `http://localhost:8080/api/v1/health`. It checks database connectivity; the database schema must already match V1–V5 for this version of the app.

## Demo steps

1. **Catalogue and EMI:** browse the actual active catalogue. If no products exist, the empty state is expected. Calculate EMI independently, then try recommendations using a requested amount and income.
2. **Policy ingestion:** enter policy name, type, version, effective date, and choose a PDF. With V4, a working OpenAI provider, and `OCR_SPACE_API_KEY` configured for scanned PDFs, wait for `INDEXED`, then inspect extracted page text. Ask a question and inspect the returned document/version/page/chunk references. Textless PDFs remain `NEEDS_OCR` when OCR is not configured; OCR provider failures show `FAILED`.
3. **Application workflow:** enter demo customer information and choose an active stored product whose range accepts the request. Create the application, then upload files with types IDENTITY, ADDRESS, and INCOME. The server recognizes PDF, PNG, and JPEG by signature.
4. **Review:** trigger deterministic checks. Missing types result in `REQUEST_ADDITIONAL_DOCUMENTS`; otherwise the response is `PENDING_HUMAN_REVIEW`. Inspect the estimate and unavailable compliance checks. No outcome approves or rejects the request.

## Verification commands

At repository root:

```powershell
docker build --build-arg SKIP_TESTS=false -t lending-backend-check ./backend
docker compose --env-file .env -f infra/docker-compose.yml config --quiet
```

In `frontend/`:

```powershell
npm run typecheck
npm run test
npm run build
```

Testcontainers tests need a Docker engine accessible to the Maven test process. A Docker build context may not expose the daemon; skipped integration tests are not passing tests.

## Troubleshooting

- **Backend exits during startup:** check container logs for missing credentials, Neon networking, or Hibernate schema validation. Confirm the exact Neon branch and reviewed schema history; leave Flyway disabled until pending migrations are confirmed.
- **Vector error / missing `vector` type:** confirm pgvector support on that Neon branch and apply reviewed V4 only after validation. Do not modify Neon extension state as an ad hoc repair.
- **Policy upload ends `FAILED`:** check `OPENAI_API_KEY`, `OCR_SPACE_API_KEY`, provider access, vector migration, and storage volume write permissions. Extracted page text remains available for diagnosis/retry.
- **AI endpoint returns 503:** provider key, connectivity, provider response, or schema is unavailable; the response is sanitized. No fake answer is produced.
- **Browser reports CORS/network error:** confirm backend is on port 8080 and set `FRONTEND_ORIGIN` to the exact UI origin before starting it.
- **No recommendations:** the application has no demo seed data. Load real catalogue rows using an approved administration process; this project does not seed or overwrite Neon data.

## Stop

```powershell
docker compose --env-file .env -f infra/docker-compose.yml down
```

This retains the named file-storage volume. `down -v` permanently removes uploaded demo files.
