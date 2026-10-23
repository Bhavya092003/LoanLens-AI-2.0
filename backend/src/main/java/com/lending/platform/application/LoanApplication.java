package com.lending.platform.application;

import com.lending.platform.common.persistence.TimestampedEntity;
import com.lending.platform.loanproduct.LoanProduct;
import com.lending.platform.user.CustomerUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

@Entity
@Table(name = "loan_applications")
public class LoanApplication extends TimestampedEntity {
    @NotBlank @Size(max = 48)
    @Column(name = "application_reference", nullable = false, unique = true, length = 48)
    private String applicationReference;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private CustomerUser user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_product_id")
    private LoanProduct loanProduct;

    @NotNull @DecimalMin(value = "0.01")
    @Column(name = "requested_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal requestedAmount;

    @Column(name = "requested_tenure_months")
    private Integer requestedTenureMonths;

    @DecimalMin(value = "0.0")
    @Column(name = "monthly_income", precision = 19, scale = 2)
    private BigDecimal monthlyIncome;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "application_status", nullable = false, length = 40)
    private ApplicationStatus applicationStatus;

    protected LoanApplication() {}

    public LoanApplication(String applicationReference, CustomerUser user, LoanProduct loanProduct,
                          BigDecimal requestedAmount, Integer requestedTenureMonths,
                          BigDecimal monthlyIncome, ApplicationStatus applicationStatus) {
        this.applicationReference = applicationReference;
        this.user = user;
        this.loanProduct = loanProduct;
        this.requestedAmount = requestedAmount;
        this.requestedTenureMonths = requestedTenureMonths;
        this.monthlyIncome = monthlyIncome;
        this.applicationStatus = applicationStatus;
    }

    public String getApplicationReference() { return applicationReference; }
    public CustomerUser getUser() { return user; }
    public LoanProduct getLoanProduct() { return loanProduct; }
    public BigDecimal getRequestedAmount() { return requestedAmount; }
    public Integer getRequestedTenureMonths() { return requestedTenureMonths; }
    public BigDecimal getMonthlyIncome() { return monthlyIncome; }
    public ApplicationStatus getApplicationStatus() { return applicationStatus; }
    public void setApplicationStatus(ApplicationStatus applicationStatus) { this.applicationStatus = applicationStatus; }
}
