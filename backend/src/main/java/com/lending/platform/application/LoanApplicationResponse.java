package com.lending.platform.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LoanApplicationResponse(UUID applicationId, String applicationReference, UUID userId,
                                      UUID loanProductId, BigDecimal requestedAmount,
                                      Integer requestedTenureMonths, BigDecimal monthlyIncome,
                                      ApplicationStatus applicationStatus, Instant createdAt, Instant updatedAt) {
    public static LoanApplicationResponse from(LoanApplication application) {
        return new LoanApplicationResponse(application.getId(), application.getApplicationReference(),
                application.getUser().getId(),
                application.getLoanProduct() == null ? null : application.getLoanProduct().getId(),
                application.getRequestedAmount(), application.getRequestedTenureMonths(),
                application.getMonthlyIncome(), application.getApplicationStatus(),
                application.getCreatedAt(), application.getUpdatedAt());
    }
}
