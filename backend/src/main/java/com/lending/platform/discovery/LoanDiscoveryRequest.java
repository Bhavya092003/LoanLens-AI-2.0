package com.lending.platform.discovery;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record LoanDiscoveryRequest(
        @Size(max = 80) String loanPurpose,
        @DecimalMin(value = "0.01") @Digits(integer = 12, fraction = 2) BigDecimal requestedAmount,
        @DecimalMin(value = "0.0") @Digits(integer = 12, fraction = 2) BigDecimal monthlyIncome,
        @DecimalMin(value = "0.0") @Digits(integer = 12, fraction = 2) BigDecimal preferredMaximumEmi,
        @Min(1) @Max(600) Integer preferredTenureMonths,
        @Size(max = 1000) String naturalLanguageRequirements) {

    @AssertTrue(message = "Provide a structured requestedAmount or naturalLanguageRequirements")
    public boolean isRequestActionable() {
        return requestedAmount != null || (naturalLanguageRequirements != null && !naturalLanguageRequirements.isBlank());
    }
}
