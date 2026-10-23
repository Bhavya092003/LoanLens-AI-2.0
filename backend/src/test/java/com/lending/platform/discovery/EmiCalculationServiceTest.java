package com.lending.platform.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class EmiCalculationServiceTest {
    private final EmiCalculationService service = new EmiCalculationService();

    @Test
    void calculatesKnownPositiveInterestExampleAndTotals() {
        EmiResponse response = service.calculate(request("100000.00", "12", 12));

        assertThat(response.estimatedEmi()).isEqualByComparingTo("8884.88");
        assertThat(response.totalAmountPayable()).isEqualByComparingTo("106618.56");
        assertThat(response.totalInterestPayable()).isEqualByComparingTo("6618.56");
    }

    @Test
    void calculatesZeroInterestExactlyToCurrencyScale() {
        EmiResponse response = service.calculate(request("1200.00", "0", 12));

        assertThat(response.estimatedEmi()).isEqualByComparingTo("100.00");
        assertThat(response.totalAmountPayable()).isEqualByComparingTo("1200.00");
        assertThat(response.totalInterestPayable()).isEqualByComparingTo("0.00");
    }

    @Test
    void supportsDecimalPrincipalRateAndRoundsHalfUp() {
        EmiResponse response = service.calculate(request("1000.01", "0.000001", 3));
        assertThat(response.estimatedEmi()).isEqualByComparingTo("333.34");
    }

    @Test
    void oneMonthTenurePaysPrincipalAndOneMonthInterest() {
        EmiResponse response = service.calculate(request("10000", "12", 1));
        assertThat(response.estimatedEmi()).isEqualByComparingTo("10100.00");
        assertThat(response.totalInterestPayable()).isEqualByComparingTo("100.00");
    }

    @Test
    void roundedInstallmentDoesNotProduceTotalPayableBelowPrincipal() {
        EmiResponse response = service.calculate(request("0.04", "0", 3));
        assertThat(response.estimatedEmi()).isEqualByComparingTo("0.02");
        assertThat(response.totalAmountPayable()).isGreaterThanOrEqualTo(response.loanAmount());
        assertThat(response.totalInterestPayable()).isEqualByComparingTo("0.02");
    }

    @Test
    void handlesMaximumSupportedPrincipalRateAndTenure() {
        EmiResponse response = service.calculate(request("999999999999.99", "100", 600));
        assertThat(response.estimatedEmi()).isPositive();
        assertThat(response.totalAmountPayable()).isGreaterThan(response.loanAmount());
    }

    private EmiRequest request(String principal, String rate, int months) {
        return new EmiRequest(new BigDecimal(principal), new BigDecimal(rate), months);
    }
}
