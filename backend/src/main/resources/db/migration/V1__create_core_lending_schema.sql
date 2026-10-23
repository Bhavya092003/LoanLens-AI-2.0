CREATE TABLE users (
    id UUID PRIMARY KEY,
    external_reference VARCHAR(160),
    full_name VARCHAR(200) NOT NULL,
    email VARCHAR(320),
    phone VARCHAR(32),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_users_full_name_nonblank CHECK (length(trim(full_name)) > 0)
);

CREATE UNIQUE INDEX uq_users_external_reference
    ON users (external_reference)
    WHERE external_reference IS NOT NULL;

CREATE TABLE loan_products (
    id UUID PRIMARY KEY,
    product_code VARCHAR(64) NOT NULL UNIQUE,
    product_name VARCHAR(200) NOT NULL,
    loan_type VARCHAR(48) NOT NULL,
    description VARCHAR(4000),
    minimum_amount NUMERIC(19, 2) NOT NULL,
    maximum_amount NUMERIC(19, 2) NOT NULL,
    minimum_annual_interest_rate NUMERIC(7, 4) NOT NULL,
    maximum_annual_interest_rate NUMERIC(7, 4) NOT NULL,
    minimum_tenure_months INTEGER NOT NULL,
    maximum_tenure_months INTEGER NOT NULL,
    minimum_monthly_income NUMERIC(19, 2),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_loan_products_code_nonblank CHECK (length(trim(product_code)) > 0),
    CONSTRAINT ck_loan_products_name_nonblank CHECK (length(trim(product_name)) > 0),
    CONSTRAINT ck_loan_products_type_nonblank CHECK (length(trim(loan_type)) > 0),
    CONSTRAINT ck_loan_products_amount_range CHECK (minimum_amount > 0 AND maximum_amount >= minimum_amount),
    CONSTRAINT ck_loan_products_interest_range CHECK (
        minimum_annual_interest_rate >= 0 AND maximum_annual_interest_rate >= minimum_annual_interest_rate
        AND maximum_annual_interest_rate <= 100
    ),
    CONSTRAINT ck_loan_products_tenure_range CHECK (
        minimum_tenure_months BETWEEN 1 AND 600 AND maximum_tenure_months BETWEEN minimum_tenure_months AND 600
    ),
    CONSTRAINT ck_loan_products_minimum_income CHECK (minimum_monthly_income IS NULL OR minimum_monthly_income >= 0)
);

CREATE INDEX ix_loan_products_active_type ON loan_products (active, loan_type);

CREATE TABLE loan_applications (
    id UUID PRIMARY KEY,
    application_reference VARCHAR(48) NOT NULL UNIQUE,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    loan_product_id UUID REFERENCES loan_products (id) ON DELETE RESTRICT,
    requested_amount NUMERIC(19, 2) NOT NULL,
    requested_tenure_months INTEGER,
    monthly_income NUMERIC(19, 2),
    application_status VARCHAR(40) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_loan_applications_reference_nonblank CHECK (length(trim(application_reference)) > 0),
    CONSTRAINT ck_loan_applications_amount CHECK (requested_amount > 0),
    CONSTRAINT ck_loan_applications_tenure CHECK (
        requested_tenure_months IS NULL OR requested_tenure_months BETWEEN 1 AND 600
    ),
    CONSTRAINT ck_loan_applications_income CHECK (monthly_income IS NULL OR monthly_income >= 0),
    CONSTRAINT ck_loan_applications_status CHECK (application_status IN (
        'DRAFT', 'SUBMITTED', 'UNDER_REVIEW', 'ADDITIONAL_DOCUMENTS_REQUIRED',
        'DECISION_PENDING', 'APPROVED', 'DECLINED', 'WITHDRAWN'
    ))
);

CREATE INDEX ix_loan_applications_user_created ON loan_applications (user_id, created_at DESC);
CREATE INDEX ix_loan_applications_product ON loan_applications (loan_product_id) WHERE loan_product_id IS NOT NULL;
CREATE INDEX ix_loan_applications_status_created ON loan_applications (application_status, created_at DESC);

CREATE TABLE application_documents (
    id UUID PRIMARY KEY,
    application_id UUID NOT NULL REFERENCES loan_applications (id) ON DELETE RESTRICT,
    document_type VARCHAR(64) NOT NULL,
    storage_provider VARCHAR(48),
    storage_key VARCHAR(512),
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(160) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    document_status VARCHAR(24) NOT NULL,
    uploaded_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_application_documents_type_nonblank CHECK (length(trim(document_type)) > 0),
    CONSTRAINT ck_application_documents_filename_nonblank CHECK (length(trim(original_filename)) > 0),
    CONSTRAINT ck_application_documents_content_type_nonblank CHECK (length(trim(content_type)) > 0),
    CONSTRAINT ck_application_documents_size CHECK (file_size_bytes >= 0),
    CONSTRAINT ck_application_documents_status CHECK (document_status IN (
        'REGISTERED', 'UPLOADED', 'PROCESSING', 'VERIFIED', 'REJECTED', 'FAILED'
    ))
);

CREATE INDEX ix_application_documents_application_uploaded
    ON application_documents (application_id, uploaded_at DESC);
CREATE INDEX ix_application_documents_status ON application_documents (document_status);

CREATE TABLE policy_documents (
    id UUID PRIMARY KEY,
    document_name VARCHAR(240) NOT NULL,
    document_type VARCHAR(64) NOT NULL,
    version VARCHAR(64) NOT NULL,
    effective_date DATE,
    status VARCHAR(24) NOT NULL,
    storage_provider VARCHAR(48),
    storage_key VARCHAR(512),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_policy_documents_name_nonblank CHECK (length(trim(document_name)) > 0),
    CONSTRAINT ck_policy_documents_type_nonblank CHECK (length(trim(document_type)) > 0),
    CONSTRAINT ck_policy_documents_version_nonblank CHECK (length(trim(version)) > 0),
    CONSTRAINT ck_policy_documents_status CHECK (status IN ('REGISTERED', 'ACTIVE', 'ARCHIVED', 'FAILED'))
);

CREATE UNIQUE INDEX uq_policy_document_name_version
    ON policy_documents (lower(document_name), version);
CREATE INDEX ix_policy_documents_type_status ON policy_documents (document_type, status);

CREATE TABLE policy_chunks (
    id UUID PRIMARY KEY,
    policy_document_id UUID NOT NULL REFERENCES policy_documents (id) ON DELETE RESTRICT,
    chunk_index INTEGER NOT NULL,
    section_title VARCHAR(240),
    chunk_text TEXT NOT NULL,
    page_number INTEGER,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_policy_chunks_index CHECK (chunk_index >= 0),
    CONSTRAINT ck_policy_chunks_text_nonblank CHECK (length(trim(chunk_text)) > 0),
    CONSTRAINT ck_policy_chunks_page CHECK (page_number IS NULL OR page_number >= 1),
    CONSTRAINT uq_policy_chunks_document_index UNIQUE (policy_document_id, chunk_index)
);

CREATE TABLE agent_checks (
    id UUID PRIMARY KEY,
    application_id UUID NOT NULL REFERENCES loan_applications (id) ON DELETE RESTRICT,
    agent_type VARCHAR(32) NOT NULL,
    check_status VARCHAR(32) NOT NULL,
    result_details JSONB NOT NULL DEFAULT '{}'::jsonb,
    requires_human_review BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_agent_checks_type CHECK (agent_type IN (
        'DOCUMENT_VERIFICATION', 'CREDIT_ASSESSMENT', 'COMPLIANCE'
    )),
    CONSTRAINT ck_agent_checks_status CHECK (check_status IN (
        'PENDING', 'PASS', 'FAIL', 'INCOMPLETE', 'NEEDS_HUMAN_REVIEW', 'ERROR'
    ))
);

CREATE INDEX ix_agent_checks_application_created ON agent_checks (application_id, created_at DESC);
CREATE INDEX ix_agent_checks_status ON agent_checks (check_status);

CREATE TABLE application_status_history (
    id UUID PRIMARY KEY,
    application_id UUID NOT NULL REFERENCES loan_applications (id) ON DELETE RESTRICT,
    previous_status VARCHAR(40),
    new_status VARCHAR(40) NOT NULL,
    changed_by UUID,
    reason VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_application_history_previous CHECK (
        previous_status IS NULL OR previous_status IN (
            'DRAFT', 'SUBMITTED', 'UNDER_REVIEW', 'ADDITIONAL_DOCUMENTS_REQUIRED',
            'DECISION_PENDING', 'APPROVED', 'DECLINED', 'WITHDRAWN'
        )
    ),
    CONSTRAINT ck_application_history_new CHECK (new_status IN (
        'DRAFT', 'SUBMITTED', 'UNDER_REVIEW', 'ADDITIONAL_DOCUMENTS_REQUIRED',
        'DECISION_PENDING', 'APPROVED', 'DECLINED', 'WITHDRAWN'
    ))
);

CREATE INDEX ix_application_history_application_created
    ON application_status_history (application_id, created_at DESC);

CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    application_id UUID REFERENCES loan_applications (id) ON DELETE RESTRICT,
    actor_type VARCHAR(24) NOT NULL,
    actor_reference VARCHAR(160),
    event_type VARCHAR(120) NOT NULL,
    event_details JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_audit_events_actor_type CHECK (actor_type IN ('USER', 'SYSTEM', 'SERVICE')),
    CONSTRAINT ck_audit_events_event_type_nonblank CHECK (length(trim(event_type)) > 0)
);

CREATE INDEX ix_audit_events_application_created ON audit_events (application_id, created_at DESC)
    WHERE application_id IS NOT NULL;
CREATE INDEX ix_audit_events_type_created ON audit_events (event_type, created_at DESC);
