# API contracts

All endpoints are under `/api/v1` and return JSON; request bodies are JSON except multipart PDF uploads. UUIDs are represented as strings. Money and rates are JSON decimal numbers backed by Java `BigDecimal` and PostgreSQL `NUMERIC`. Paginated endpoints use query parameters `page` (default `0`) and `size` (default `20`, range `1`–`100`) and return `{ "content": [], "page": 0, "size": 20, "totalElements": 0, "totalPages": 0 }`.

## Error response

Errors use a shared envelope:

```json
{
  "status": 400,
  "errorCode": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "timestamp": "2026-10-23T12:00:00Z",
  "path": "/api/v1/loan-discovery/emi",
  "fieldErrors": [{ "field": "loanAmount", "message": "must be greater than 0.01" }]
}
```

## Authentication

`POST /auth/register` accepts `username`, `password`, `fullName`, and optional
`email`; it creates a `CUSTOMER` account and returns a JWT. `POST /auth/login`
accepts `username` and `password` and returns a JWT. Send the token on later
requests as `Authorization: Bearer <token>`. `GET /auth/me` returns the current
user and role. Reviewer accounts are provisioned from the configured reviewer
environment values and cannot be created through public registration.
Authenticated reviewers may create additional reviewer accounts through
`POST /auth/reviewers`; this endpoint is not available to customers.

## Demo authentication

Authentication uses a session cookie. `POST /auth/login` accepts URL-encoded
`username` and `password`; `GET /auth/me` returns the current identity and
roles; `POST /auth/logout` clears the session. `CUSTOMER` users create
applications and upload documents. `REVIEWER` users can access
`GET /applications/review-queue`, run or inspect application reviews, and are
the only role allowed to process reviewer tasks. Unauthenticated requests to
`/auth/me` and application data return `401`; customer attempts to access
reviewer operations return `403`.

Error codes are stable machine-readable identifiers. Internal exceptions return a generic 500 message; SQL details, stack traces, credentials, and tokens are not returned.

PDF upload errors also use this envelope. Invalid, empty, encrypted, malformed, and unsupported PDFs return HTTP 400. File or request size limits return HTTP 413. Processing failures are returned as a document with status `FAILED` and a sanitized `processingError`; retries return 409 when a document is already processing or otherwise ineligible.

## Endpoints

| Method and path | Contract | Status |
|---|---|---|
| `GET /loan-products` | Query: optional `loanType` (case-insensitive exact match), `amount` (nonnegative amount within product bounds); `page` (default `0`, minimum `0`), `size` (default `20`, range `1`–`100`). Active products only, ordered by `productName ASC, id ASC`; returns `content`, `page`, `size`, `totalElements`, and `totalPages`. | Implemented; 200/400 |
| `GET /loan-products/{productId}` | Returns the product response; unknown UUID returns the shared 404 envelope. | Implemented; 200/404 |
| `POST /loan-discovery/recommendations` | Structured request fields: optional `loanPurpose`, `requestedAmount`, `monthlyIncome`, `preferredMaximumEmi`, `preferredTenureMonths`; free text is interpreted through the configured chat provider into bounded criteria, then Java validates all values and performs persisted-catalogue matching/EMI ranking. Without a provider, fields are not inferred and the response requests structured details. | Implemented; 200/400 |
| `POST /loan-discovery/emi` | Request: `{ "loanAmount": 100000.00, "annualInterestRate": 12.0, "tenureMonths": 12 }`. Returns `{ "loanAmount": 100000.00, "annualInterestRate": 12.0, "tenureMonths": 12, "estimatedEmi": 8884.88, "totalAmountPayable": 106618.56, "totalInterestPayable": 6618.56, "currency": "INR" }`. Principal must be `0.01`–`999999999999.99` with at most two fractional digits; annual percentage rate `0`–`100` with at most six fractional digits; tenure `1`–`600` months. | Implemented; 200/400 |
| `POST /policy-questions` | Request: `{ "question": "What are the foreclosure terms?", "context": null, "documentType": null, "version": null }`. Retrieves only `INDEXED` documents using pgvector cosine similarity, then asks the configured chat provider to answer from a bounded evidence prompt. Sources include actual document/version/page and document/chunk IDs. No evidence returns an abstention. AI and pgvector must be configured. | Implemented; 200/400/503 |
| `POST /policies/documents` with `application/json` | Legacy metadata registration request: `{ "documentName": "Vehicle Loan Policy", "documentType": "LENDING_POLICY", "version": "2026.1", "effectiveDate": "2026-07-01" }`. Returns status `REGISTERED`; no file is claimed as uploaded. Duplicate name/version returns 409. | Implemented; 201/400/409 |
| `POST /policies/documents` with `multipart/form-data` | Required fields: `file` (PDF), `documentName`, `documentType`, `version`; optional `effectiveDate` (ISO date). Default file limit 20 MB and multipart limit 21 MB. The server validates/parses the PDF, extracts page text, chunks it, creates embeddings, then persists vectors. `INDEXED` means retrieval indexing committed; provider/vector failures preserve extracted pages and return `FAILED` with retry available. | Implemented; 201/400/409/413 |
| `GET /policies/documents` | Paginated list (default page 0, size 20; maximum 100), stably ordered by creation descending then ID ascending. Optional exact version/status and case-insensitive document type filters. No binary data or storage paths. | Implemented; 200/400 |
| `GET /policies/documents/{documentId}` | Returns document metadata, processing status, file size, extraction completion time and sanitized failure message, or 404. No storage paths. | Implemented; 200/404 |
| `GET /policies/documents/{documentId}/pages` | Paginated extracted page text in ascending page number order. Each row has document ID, one-based page number, text and creation timestamp. Text is only returned for the specified document. | Implemented; 200/400/404 |
| `POST /policies/documents/{documentId}/retry` | Retries extraction, optional OCR, chunking, and indexing for eligible `UPLOADED` or `FAILED` documents. Other states, including `PROCESSING`, `INDEXED`, and `NEEDS_OCR`, return 409. Textless PDFs use OCR.Space when `OCR_SPACE_API_KEY` is configured. | Implemented; 200/404/409 |
| `POST /customers` | Creates a minimal demo customer profile from `fullName`, optional `email`, and optional `phone`. This does not verify identity; the endpoint remains available for the customer demo workflow. | Implemented; 201/400 |
| `POST /applications` | CUSTOMER role required. Request: `{ "userId": "uuid", "loanProductId": "uuid-or-null", "requestedAmount": 500000, "requestedTenureMonths": 60, "monthlyIncome": 60000 }`. User must already exist; an optional product must exist. Returns a generated application reference and initial `DRAFT` status. | Implemented; 201/400/401/403/404 |
| `POST /applications/{applicationId}/submit` | CUSTOMER role required. Moves a draft application to `SUBMITTED` and adds it to the reviewer queue. | Implemented; 200/401/403/404/409 |
| `GET /applications/review-queue` | REVIEWER role required. Returns up to 50 applications in submitted, review, decision-pending, or additional-documents-required states, ordered by most recently updated. | Implemented; 200/401/403 |
| `POST /auth/reviewers` | REVIEWER role required. Creates another database-backed reviewer account. | Implemented; 201/400/401/403/409 |
| `GET /applications/{applicationId}` | CUSTOMER or REVIEWER role required. Returns application IDs/reference, amount, optional tenure/income/product, status, and timestamps, or 404. | Implemented; 200/401/403/404 |
| `POST /applications/{applicationId}/documents` | CUSTOMER role required. Multipart fields: `documentType`, `file`. Accepts PDF, PNG, and JPEG based on file signature, with a 10 MB limit. Uses generated keys and the separate applications directory on the Docker volume; metadata returns no storage key/path. `UPLOADED` does not mean verified. | Implemented; 201/400/401/403/404/413/415 |
| `GET /applications/{applicationId}/documents` | CUSTOMER or REVIEWER role required. Paginated persisted document metadata. Unknown application returns 404; no records are fabricated. | Implemented; 200/401/403/404 |
| `POST /applications/{applicationId}/review` | REVIEWER role required. Request retains `requestedChecks` and optional `reviewNote`; MVP runs deterministic document presence, indicative EMI-to-declared-income, and explicit unavailable compliance checks. Persists checks, status history, and audit event. Never approves/rejects. | Implemented; 200/400/401/403/404/409 |
| `POST /applications/{applicationId}/decision` | REVIEWER role required. Records the human decision `APPROVE`, `DECLINE`, or `REQUEST_DOCUMENTS`, optional note, and `requestedDocumentTypes` (required for `REQUEST_DOCUMENTS`), status history, and audit event. | Implemented; 200/400/401/403/404/409 |
| `GET /applications/{applicationId}/review` | Authenticated customer or reviewer. Returns automated findings, individual persisted checks, human decision/reviewer/note/timestamp, exact requested documents, next steps, and human-approval requirement. | Implemented; 200/401/403/404 |

Policy statuses: `PROCESSED` means page extraction completed; `INDEXED` means text chunks and vectors committed; `NEEDS_OCR` means no extractable text was found and OCR is not configured; `FAILED` means extraction, OCR, or indexing failed and retry is allowed. Textless PDFs are sent to OCR.Space when configured. Similarity is `1 - cosine_distance`, not factual confidence. The current response confidence is a coarse evidence-count category, not model certainty. The policy document text is untrusted input and the answer prompt bounds evidence. Files use generated keys on a Docker volume; paths are not returned.

The product catalogue has no seed data. `/customers` creates demo profiles, but ownership, identity proof, and verified customer status are not implemented. Required document-type checklist is the demo set `IDENTITY`, `ADDRESS`, `INCOME`; presence is not verification. `APPROVED`/`DECLINED` are not exposed through an API; human approval remains outside this demo.

### Recommendation flow and limitations

Loan discovery uses `ProviderLoanRequirementInterpreter` when free text is supplied. It asks the configured provider for JSON criteria, validates numeric bounds and field shapes in Java, and uses only the persisted catalogue and deterministic Java calculations for matching. Provider failure or malformed output falls back to the explicitly supplied structured fields and asks the user to clarify missing criteria. The provider does not select products, change rates, calculate EMI, or make lending decisions. Live provider operation is not verified in local tests.

Candidates come from the active persisted catalogue through the existing paginated repository query, filtered by exact case-insensitive loan type when specified and product amount range. When reported monthly income is below a product's stored minimum, that candidate is excluded; if income is missing and a threshold exists, the candidate is retained as unknown. Supported tenure months are examined from the stored minimum through maximum. A supported tenure that meets the preferred EMI ceiling is favored; among those, the tenure closest to the preferred term is selected. With no preferred term, the lowest EMI supported term is selected. Product ranking puts budget-fitting results first, then results whose estimated EMI does not exceed reported income, then tenure preference, lower EMI, product name, and product UUID. Repository pages are read in product-name/UUID order, so ties resolve consistently.

For calculations, the stored maximum annual rate is used as a conservative catalogue scenario. It is not an advertised or guaranteed lender offer. EMI is calculated only when the persisted rate range is present and internally valid, using the same `EmiCalculationService` and its documented rounding/input limits. A product with missing rate information can appear with null rate/EMI and `INSUFFICIENT_INFORMATION`; no value is fabricated. Minimum income and EMI-to-reported-income comparisons are catalogue/affordability signals only, not eligibility or approval decisions.

`LIKELY_ELIGIBLE` means the amount/type/tenure match, a stored minimum-income threshold exists and is met, and both reported income and the preferred EMI ceiling (if provided) cover the estimate; it is not an underwriting result or approval. `MAY_BE_ELIGIBLE` means the EMI exceeds the preferred ceiling or reported income, while the product otherwise matches the catalogue filters. `INSUFFICIENT_INFORMATION` means reported income, a product minimum-income threshold, or usable product rate data is absent. `UNLIKELY_ELIGIBLE` remains in the shared enum for compatibility; current deterministic filtering excludes known amount/type/minimum-income mismatches instead of returning them as recommendations.

### EMI calculation policy

The deterministic backend service uses the standard reducing-balance formula `EMI = P × r × (1+r)^n / ((1+r)^n - 1)`, where `r = annualInterestRate / 1200`, and `n` is months. At zero interest, it uses `P / n`. Calculations use `BigDecimal` with DECIMAL128 intermediate precision; the final monthly EMI is rounded to two decimals using `HALF_UP`. To avoid a rounded installment total below principal, the installment is raised to the smallest cent amount whose product with the tenure covers principal. Total payable is the displayed rounded EMI multiplied by the number of months, rounded to cents; total interest is total payable minus principal, rounded to cents. Currency defaults to INR, consistent with the existing contract. These are estimates for the supplied rate and tenure; lender schedules can differ due to fees, taxes, insurance, and lender-specific rounding.
