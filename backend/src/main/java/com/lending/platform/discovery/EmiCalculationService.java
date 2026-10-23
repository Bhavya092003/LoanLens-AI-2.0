package com.lending.platform.discovery;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;

@Service
public class EmiCalculationService {
    private static final MathContext CALCULATION_CONTEXT = MathContext.DECIMAL128;
    private static final BigDecimal TWELVE_HUNDRED = BigDecimal.valueOf(1200);
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final int MONEY_SCALE = 2;
    private static final RoundingMode MONEY_ROUNDING = RoundingMode.HALF_UP;

    public EmiResponse calculate(EmiRequest request) {
        BigDecimal principal = request.loanAmount();
        BigDecimal annualRate = request.annualInterestRate();
        int months = request.tenureMonths();

        BigDecimal emi = annualRate.signum() == 0
                ? principal.divide(BigDecimal.valueOf(months), CALCULATION_CONTEXT)
                : calculateReducingBalanceEmi(principal, annualRate, months);
        emi = emi.setScale(MONEY_SCALE, MONEY_ROUNDING);
        BigDecimal minimumInstallment = principal.divide(BigDecimal.valueOf(months), MONEY_SCALE, RoundingMode.CEILING);
        emi = emi.max(minimumInstallment);

        BigDecimal totalPayable = emi.multiply(BigDecimal.valueOf(months)).setScale(MONEY_SCALE, MONEY_ROUNDING);
        BigDecimal totalInterest = totalPayable.subtract(principal).setScale(MONEY_SCALE, MONEY_ROUNDING);
        return new EmiResponse(principal, annualRate, months, emi, totalPayable, totalInterest, "INR");
    }

    private BigDecimal calculateReducingBalanceEmi(BigDecimal principal, BigDecimal annualRate, int months) {
        BigDecimal monthlyRate = annualRate.divide(TWELVE_HUNDRED, CALCULATION_CONTEXT);
        BigDecimal factor = ONE.add(monthlyRate, CALCULATION_CONTEXT).pow(months, CALCULATION_CONTEXT);
        BigDecimal numerator = principal.multiply(monthlyRate, CALCULATION_CONTEXT)
                .multiply(factor, CALCULATION_CONTEXT);
        BigDecimal denominator = factor.subtract(ONE, CALCULATION_CONTEXT);
        return numerator.divide(denominator, CALCULATION_CONTEXT);
    }
}
