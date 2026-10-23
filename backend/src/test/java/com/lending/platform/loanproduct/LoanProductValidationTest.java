package com.lending.platform.loanproduct;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class LoanProductValidationTest {
    @Test
    void rejectsInconsistentAmountInterestAndTenureRanges() {
        LoanProduct product = new LoanProduct("TEST", "Test", "AUTO", null,
                new BigDecimal("10"), new BigDecimal("5"), new BigDecimal("12"),
                new BigDecimal("10"), 24, 12, new BigDecimal("0"), true);

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var violations = factory.getValidator().validate(product);
            assertThat(violations).extracting(v -> v.getMessage())
                    .contains("minimumAmount must not exceed maximumAmount",
                            "minimumAnnualInterestRate must not exceed maximumAnnualInterestRate",
                            "tenure range must be positive and minimumTenureMonths must not exceed maximumTenureMonths");
        }
    }
}
