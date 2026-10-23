package com.lending.platform.discovery;

import java.math.BigDecimal;

public record EmiResponse(BigDecimal loanAmount, BigDecimal annualInterestRate, int tenureMonths,
                          BigDecimal estimatedEmi, BigDecimal totalAmountPayable,
                          BigDecimal totalInterestPayable, String currency) {
}
