package com.lending.platform.loanproduct;

import java.math.BigDecimal;
import java.util.UUID;

public record LoanProductResponse(
        UUID productId,
        String productCode,
        String productName,
        String loanType,
        String description,
        BigDecimal minimumAmount,
        BigDecimal maximumAmount,
        BigDecimal minimumAnnualInterestRate,
        BigDecimal maximumAnnualInterestRate,
        int minimumTenureMonths,
        int maximumTenureMonths,
        BigDecimal minimumMonthlyIncome,
        boolean active) {
    public static LoanProductResponse from(LoanProduct product) {
        return new LoanProductResponse(product.getId(), product.getProductCode(), product.getProductName(),
                product.getLoanType(), product.getDescription(), product.getMinimumAmount(), product.getMaximumAmount(),
                product.getMinimumAnnualInterestRate(), product.getMaximumAnnualInterestRate(),
                product.getMinimumTenureMonths(), product.getMaximumTenureMonths(),
                product.getMinimumMonthlyIncome(), product.isActive());
    }
}
