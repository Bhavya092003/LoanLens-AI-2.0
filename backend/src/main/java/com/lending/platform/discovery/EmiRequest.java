package com.lending.platform.discovery;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record EmiRequest(
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 12, fraction = 2) BigDecimal loanAmount,
        @NotNull @DecimalMin(value = "0.0") @DecimalMax(value = "100.0") @Digits(integer = 3, fraction = 6)
        BigDecimal annualInterestRate,
        @NotNull @Positive @Max(600) Integer tenureMonths) {
}
