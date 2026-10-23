package com.lending.platform.application;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateApplicationRequest(
        @NotNull UUID userId,
        UUID loanProductId,
        @NotNull @DecimalMin(value = "0.01") BigDecimal requestedAmount,
        @Min(1) @Max(600) Integer requestedTenureMonths,
        @DecimalMin(value = "0.0") BigDecimal monthlyIncome) {
}
