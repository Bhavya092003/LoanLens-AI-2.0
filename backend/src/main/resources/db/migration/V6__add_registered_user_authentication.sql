ALTER TABLE users
    ADD COLUMN auth_username VARCHAR(120),
    ADD COLUMN password_hash VARCHAR(255),
    ADD COLUMN role VARCHAR(24),
    ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE;

CREATE UNIQUE INDEX uq_users_auth_username
    ON users (lower(auth_username))
    WHERE auth_username IS NOT NULL;

ALTER TABLE users
    ADD CONSTRAINT ck_users_role
    CHECK (role IS NULL OR role IN ('CUSTOMER', 'REVIEWER'));
