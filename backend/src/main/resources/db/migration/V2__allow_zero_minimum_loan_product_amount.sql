ALTER TABLE loan_products
    DROP CONSTRAINT ck_loan_products_amount_range;

ALTER TABLE loan_products
    ADD CONSTRAINT ck_loan_products_amount_range
        CHECK (minimum_amount >= 0 AND maximum_amount >= minimum_amount);
