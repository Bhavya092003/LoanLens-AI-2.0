# Lendwise digital lending demo

Java 21 + Spring Boot modular monolith with a React/TypeScript client for catalogue discovery, policy document Q&A, and a human-reviewed loan application workflow. PostgreSQL is hosted on Neon. There is no Node.js business backend and no local PostgreSQL container.

## Technology and prerequisites

- Docker Desktop/Engine with Compose. The multi-stage backend image builds with Maven and Java 21, then runs on a Java 21 JRE. Local Maven/JDK are not required.
- A Neon PostgreSQL database and role credentials.
- Node.js/npm only for local React/Vite build, type checking, and tests. Production business APIs run only in the Java container.
- An OpenAI API key for policy embeddings and policy Q&A. The embedding default is `text-embedding-3-small` at 1536 dimensions; chat defaults to `gpt-4o-mini`. Provider integration and model details: [embeddings guide](https://developers.openai.com/api/docs/guides/embeddings) and [embedding model](https://developers.openai.com/api/docs/models/text-embedding-3-small).

## Configure local environment

At the repository root:

```powershell
Copy-Item .env.example .env
```

Set `NEON_DATABASE_URL`, `NEON_DATABASE_USERNAME`, and `NEON_DATABASE_PASSWORD` from the intended Neon branch's Connect dialog. Keep TLS enabled (`sslmode=require`). Set `JWT_SECRET` to at least 32 random characters. New customers register through the frontend or `POST /api/v1/auth/register`; the configured reviewer account is provisioned into the database at startup. Optionally set `OPENAI_API_KEY` to enable embeddings and policy answers. OCR configuration is available through `OCR_PROVIDER`, `OCR_SPACE_API_KEY`, `OCR_SPACE_API_URL`, `OCR_SPACE_LANGUAGE`, and `OCR_SPACE_ENGINE`; textless PDF policy uploads use OCR.Space when configured. `.env` is ignored by Git; only placeholders/defaults belong in `.env.example`. Do not put provider or database credentials in frontend variables.

## Demo authentication and roles

The app uses database-backed JWT authentication. Customers self-register and receive the
`CUSTOMER` role. The configured reviewer identity is bootstrapped into the database with the
`REVIEWER` role; reviewer self-registration is intentionally disabled. Customers create
applications and upload documents, while reviewers access the review queue and run review checks.
`POST /api/v1/applications/{id}/review` requires `REVIEWER`. This is suitable for a local demo only;
use an external identity provider, refresh-token rotation, secret management, and audit controls
before production use.

## Database setup and migration safety

Flyway is disabled by default and Hibernate is configured to validate only. Migrations V1–V3 define the core/application/policy schema. V4 adds pgvector, `vector(1536)`, `INDEXED`, and an HNSW cosine index. V5 adds the application document checksum and duplicate-upload uniqueness. These are generated changes and have not been applied. Before applying anything, confirm the target Neon project/branch/database, inspect its `flyway_schema_history` and existing schema, and check that pgvector is available. Extension availability on the configured Neon target is unverified.

Never blindly run V1–V5 against a non-empty/existing database. Review each pending SQL file and take a restorable Neon branch point. For a confirmed new demo branch, the migration operation is opt-in:

```powershell
docker compose --env-file .env -f infra/docker-compose.yml run --rm -e FLYWAY_ENABLED=true backend
```

This one-off starts the backend and exits on startup failure; stop it after Flyway completes, then keep `FLYWAY_ENABLED=false` for routine starts. Check the Flyway logs. If the existing schema conflicts, stop and investigate rather than baselining or modifying history. Read [database schema](docs/database-schema.md) for the exact migration contents.

## Run the backend

After the correct schema is present and reviewed:

```powershell
docker compose --env-file .env -f infra/docker-compose.yml up --build -d backend
docker compose --env-file .env -f infra/docker-compose.yml logs -f backend
```

Health endpoint: `GET http://localhost:8080/api/v1/health`. Compose starts only the backend, which connects to Neon. Policy and application PDFs/images use generated storage keys on the named Docker volume. `docker compose down` preserves uploaded files; `docker compose down -v` deletes them.

## Run the React UI

From `frontend/` (Node.js/npm are frontend tooling only):

```powershell
npm ci
npm run dev
```

The app is served at `http://localhost:5173` and uses `FRONTEND_API_BASE_URL` from the root `.env` (default `http://localhost:8080`). Checks:

```powershell
npm run typecheck
npm run test
npm run build
```

## Demo walkthrough

1. Open Digital Loan Discovery, browse products actually stored in the catalogue, calculate EMI, and request recommendations. No products are seeded.
2. In Policy Assistant, upload a versioned PDF and inspect processing status/page text. `INDEXED` requires V4, pgvector, and `OPENAI_API_KEY`; `NEEDS_OCR` means extraction found no text. Ask a question only after successful indexing.
3. Create a demo customer profile and application using an active persisted product, upload IDENTITY, ADDRESS, and INCOME files (signature-checked PDF/PNG/JPEG), then run the deterministic review.
4. Demonstrate missing-document handling or a complete checklist. The review is always pending a human decision; this demo does not authenticate users or approve/reject loans.

See [API contracts](docs/api-contracts.md), [architecture](docs/architecture.md), [schema and migration review](docs/database-schema.md), and [demo runbook](docs/demo-runbook.md).

## Known limitations

- Authentication, authorization, identity verification, and human approval controls are not implemented. Do not expose this demo publicly or use it for lending decisions.
- Catalogue products are not seeded. Recommendation matching and EMI calculations are deterministic; free-text interpretation requires `OPENAI_API_KEY` and was not live-verified.
- OCR provider settings are configurable, and textless PDF policy uploads can be processed through OCR.Space. Document upload indicates storage only, not document authenticity.
- RAG needs a provider key and V4 on a confirmed Neon branch. Neither provider calls nor pgvector/Neon migration application is claimed verified by local builds.
- Filesystem uploads on a Docker volume are demo storage, not managed object storage or a production backup strategy.
- Compliance checks (KYC, sanctions, bureau, regulatory) are marked unavailable/human-review; no such integrations ran.
