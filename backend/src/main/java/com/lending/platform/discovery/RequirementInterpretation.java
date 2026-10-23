package com.lending.platform.discovery;

import java.math.BigDecimal;

public record RequirementInterpretation(
        String loanPurpose,
        BigDecimal requestedAmount,
        BigDecimal monthlyIncome,
        BigDecimal preferredMaximumEmi,
        Integer preferredTenureMonths,
        String interpretedRequirement,
        String clarification) {
}
