package com.lending.platform.discovery;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record LoanRecommendationResponse(
        String interpretedRequirement,
        BigDecimal loanAmount,
        BigDecimal monthlyIncome,
        BigDecimal maxPreferredEmi,
        String currency,
        List<Recommendation> recommendations,
        String disclaimer,
        String clarifyingQuestion) {

    public record Recommendation(
            UUID productId,
            String productName,
            BigDecimal indicativeInterestRate,
            int tenureMonths,
            BigDecimal estimatedEmi,
            String reason,
            EligibilityIndicator eligibilityIndicator) {}
}
