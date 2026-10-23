package com.lending.platform.loanproduct;

import com.lending.platform.common.persistence.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

@Entity
@Table(name = "loan_products")
public class LoanProduct extends TimestampedEntity {
    @NotBlank @Size(max = 64)
    @Column(name = "product_code", nullable = false, unique = true, length = 64)
    private String productCode;

    @NotBlank @Size(max = 200)
    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    @NotBlank @Size(max = 48)
    @Column(name = "loan_type", nullable = false, length = 48)
    private String loanType;

    @Size(max = 4000)
    @Column(name = "description", length = 4000)
    private String description;

    @NotNull @DecimalMin(value = "0.00") @Digits(integer = 17, fraction = 2)
    @Column(name = "minimum_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal minimumAmount;

    @NotNull @DecimalMin(value = "0.00") @Digits(integer = 17, fraction = 2)
    @Column(name = "maximum_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal maximumAmount;

    @NotNull @DecimalMin(value = "0.0") @DecimalMax(value = "100.0") @Digits(integer = 3, fraction = 4)
    @Column(name = "minimum_annual_interest_rate", nullable = false, precision = 7, scale = 4)
    private BigDecimal minimumAnnualInterestRate;

    @NotNull @DecimalMin(value = "0.0") @DecimalMax(value = "100.0") @Digits(integer = 3, fraction = 4)
    @Column(name = "maximum_annual_interest_rate", nullable = false, precision = 7, scale = 4)
    private BigDecimal maximumAnnualInterestRate;

    @Column(name = "minimum_tenure_months", nullable = false)
    @Min(1) @Max(600)
    private int minimumTenureMonths;

    @Column(name = "maximum_tenure_months", nullable = false)
    @Min(1) @Max(600)
    private int maximumTenureMonths;

    @DecimalMin(value = "0.0") @Digits(integer = 17, fraction = 2)
    @Column(name = "minimum_monthly_income", precision = 19, scale = 2)
    private BigDecimal minimumMonthlyIncome;

    @Column(name = "active", nullable = false)
    private boolean active;

    protected LoanProduct() {}

    public LoanProduct(String productCode, String productName, String loanType, String description,
                       BigDecimal minimumAmount, BigDecimal maximumAmount,
                       BigDecimal minimumAnnualInterestRate, BigDecimal maximumAnnualInterestRate,
                       int minimumTenureMonths, int maximumTenureMonths,
                       BigDecimal minimumMonthlyIncome, boolean active) {
        this.productCode = productCode;
        this.productName = productName;
        this.loanType = loanType;
        this.description = description;
        this.minimumAmount = minimumAmount;
        this.maximumAmount = maximumAmount;
        this.minimumAnnualInterestRate = minimumAnnualInterestRate;
        this.maximumAnnualInterestRate = maximumAnnualInterestRate;
        this.minimumTenureMonths = minimumTenureMonths;
        this.maximumTenureMonths = maximumTenureMonths;
        this.minimumMonthlyIncome = minimumMonthlyIncome;
        this.active = active;
    }

    public String getProductCode() { return productCode; }
    public String getProductName() { return productName; }
    public String getLoanType() { return loanType; }
    public String getDescription() { return description; }
    public BigDecimal getMinimumAmount() { return minimumAmount; }
    public BigDecimal getMaximumAmount() { return maximumAmount; }
    public BigDecimal getMinimumAnnualInterestRate() { return minimumAnnualInterestRate; }
    public BigDecimal getMaximumAnnualInterestRate() { return maximumAnnualInterestRate; }
    public int getMinimumTenureMonths() { return minimumTenureMonths; }
    public int getMaximumTenureMonths() { return maximumTenureMonths; }
    public BigDecimal getMinimumMonthlyIncome() { return minimumMonthlyIncome; }
    public boolean isActive() { return active; }

    @AssertTrue(message = "minimumAmount must not exceed maximumAmount")
    public boolean isAmountRangeValid() {
        return minimumAmount == null || maximumAmount == null || minimumAmount.compareTo(maximumAmount) <= 0;
    }

    @AssertTrue(message = "minimumAnnualInterestRate must not exceed maximumAnnualInterestRate")
    public boolean isInterestRangeValid() {
        return minimumAnnualInterestRate == null || maximumAnnualInterestRate == null
                || minimumAnnualInterestRate.compareTo(maximumAnnualInterestRate) <= 0;
    }

    @AssertTrue(message = "tenure range must be positive and minimumTenureMonths must not exceed maximumTenureMonths")
    public boolean isTenureRangeValid() {
        return minimumTenureMonths > 0 && maximumTenureMonths >= minimumTenureMonths;
    }

    @AssertTrue(message = "minimumMonthlyIncome must be nonnegative when provided")
    public boolean isMinimumMonthlyIncomeValid() {
        return minimumMonthlyIncome == null || minimumMonthlyIncome.signum() >= 0;
    }
}
