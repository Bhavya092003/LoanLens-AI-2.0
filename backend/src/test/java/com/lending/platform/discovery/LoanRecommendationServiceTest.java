package com.lending.platform.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lending.platform.loanproduct.LoanProduct;
import com.lending.platform.loanproduct.LoanProductRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

class LoanRecommendationServiceTest {
    private final LoanProductRepository repository = org.mockito.Mockito.mock(LoanProductRepository.class);
    private final EmiCalculationService emiService = new EmiCalculationService();
    private final LoanRequirementInterpreter interpreter = new StructuredLoanRequirementInterpreter();
    private final LoanRecommendationService service =
            new LoanRecommendationService(interpreter, repository, emiService);

    @Test
    void recommendsPersistedMatchingProductUsingStoredUpperRateBoundAndEmiService() {
        LoanProduct product = product("Used auto", "AUTO", true, "8", "12", 24, 60, "25000");
        stubCatalogue(product);

        LoanRecommendationResponse response = service.recommend(request("AUTO", "500000", "60000", "12000", 60));

        assertThat(response.recommendations()).hasSize(1);
        var recommendation = response.recommendations().getFirst();
        assertThat(recommendation.productId()).isEqualTo(product.getId());
        assertThat(recommendation.indicativeInterestRate()).isEqualByComparingTo("12");
        assertThat(recommendation.tenureMonths()).isEqualTo(60);
        assertThat(recommendation.estimatedEmi()).isEqualByComparingTo("11122.22");
        assertThat(recommendation.eligibilityIndicator()).isEqualTo(EligibilityIndicator.LIKELY_ELIGIBLE);
        assertThat(recommendation.reason()).contains("not as an offer", "not a credit assessment");
    }

    @Test
    void returnsEmptyRecommendationsWhenNoActiveCatalogueProductMatches() {
        stubCatalogue();
        LoanRecommendationResponse response = service.recommend(request("AUTO", "500000", null, null, null));

        assertThat(response.recommendations()).isEmpty();
        assertThat(response.clarifyingQuestion()).contains("No active catalogue products match");
    }

    @Test
    void excludesInactiveProductsEvenIfRepositoryStubReturnsOne() {
        stubCatalogue(product("Inactive", "AUTO", false, "8", "12", 12, 60, null));
        LoanRecommendationResponse response = service.recommend(request("AUTO", "500000", "60000", null, null));
        assertThat(response.recommendations()).isEmpty();
    }

    @Test
    void excludesProductsBelowTheirRecordedMinimumIncome() {
        stubCatalogue(product("Income restricted", "AUTO", true, "8", "12", 12, 60, "70000"));
        LoanRecommendationResponse response = service.recommend(request("AUTO", "500000", "60000", null, null));

        assertThat(response.recommendations()).isEmpty();
        assertThat(response.clarifyingQuestion()).contains("minimum-income requirements");
    }

    @Test
    void returnsNoMatchWhenRequestedAmountIsOutsidePersistedProductBounds() {
        stubCatalogue(product("Small loan", "AUTO", true, "8", "12", 12, 60, null));
        LoanRecommendationResponse response = service.recommend(request("AUTO", "5000000", "60000", null, null));
        assertThat(response.recommendations()).isEmpty();
        assertThat(response.clarifyingQuestion()).contains("No active catalogue products match");
    }

    @Test
    void marksEligibilityUnknownWhenIncomeIsMissingAndNoThresholdIsRecorded() {
        stubCatalogue(product("No threshold", "AUTO", true, "8", "12", 12, 60, null));
        LoanRecommendationResponse response = service.recommend(request("AUTO", "500000", null, null, 36));

        assertThat(response.recommendations().getFirst().eligibilityIndicator())
                .isEqualTo(EligibilityIndicator.INSUFFICIENT_INFORMATION);
    }

    @Test
    void marksEligibilityUnknownWhenMinimumIncomeCriterionIsNotRecorded() {
        stubCatalogue(product("No threshold", "AUTO", true, "8", "12", 12, 60, null));
        LoanRecommendationResponse response = service.recommend(request("AUTO", "500000", "60000", null, 36));
        assertThat(response.recommendations().getFirst().eligibilityIndicator())
                .isEqualTo(EligibilityIndicator.INSUFFICIENT_INFORMATION);
    }

    @Test
    void selectsClosestSupportedTermThatMeetsBudgetBeforePreferredDistance() {
        stubCatalogue(product("Short term catalogue", "AUTO", true, "12", "12", 12, 36, null));
        LoanRecommendationResponse response = service.recommend(request("AUTO", "500000", "60000", "20000", 60));

        assertThat(response.recommendations().getFirst().tenureMonths()).isEqualTo(36);
        assertThat(response.recommendations().getFirst().estimatedEmi()).isLessThanOrEqualTo(new BigDecimal("20000"));
    }

    @Test
    void withoutPreferredTenureSelectsLowestEmiAmongSupportedTerms() {
        stubCatalogue(product("Flexible term", "AUTO", true, "12", "12", 12, 36, null));
        LoanRecommendationResponse response = service.recommend(request("AUTO", "500000", "60000", null, null));
        assertThat(response.recommendations().getFirst().tenureMonths()).isEqualTo(36);
    }

    @Test
    void labelsRecommendationWhenNoSupportedTermMeetsPreferredEmi() {
        stubCatalogue(product("Short term catalogue", "AUTO", true, "12", "12", 12, 12, "0"));
        LoanRecommendationResponse response = service.recommend(request("AUTO", "500000", "60000", "1000", 12));
        var recommendation = response.recommendations().getFirst();
        assertThat(recommendation.eligibilityIndicator()).isEqualTo(EligibilityIndicator.MAY_BE_ELIGIBLE);
        assertThat(recommendation.reason()).contains("exceeds the stated maximum");
    }

    @Test
    void ranksCandidatesDeterministicallyByFitThenName() {
        LoanProduct beta = product("Beta", "AUTO", true, "10", "10", 12, 24, null);
        LoanProduct alpha = product("Alpha", "AUTO", true, "10", "10", 12, 24, null);
        stubCatalogue(beta, alpha);

        LoanRecommendationResponse response = service.recommend(request("AUTO", "100000", "50000", null, 12));

        assertThat(response.recommendations()).extracting(LoanRecommendationResponse.Recommendation::productName)
                .containsExactly("Alpha", "Beta");
    }

    @Test
    void doesNotFabricateRateOrEmiWhenCatalogueRateIsMissing() {
        stubCatalogue(product("Incomplete rate", "AUTO", true, null, null, 12, 24, null));
        LoanRecommendationResponse response = service.recommend(request("AUTO", "100000", "50000", null, null));

        var recommendation = response.recommendations().getFirst();
        assertThat(recommendation.indicativeInterestRate()).isNull();
        assertThat(recommendation.estimatedEmi()).isNull();
        assertThat(recommendation.eligibilityIndicator()).isEqualTo(EligibilityIndicator.INSUFFICIENT_INFORMATION);
        assertThat(recommendation.reason()).contains("no EMI was calculated");
    }

    @Test
    void naturalLanguageOnlyRequestReturnsClarificationWithoutGuessingOrQueryingCatalogue() {
        LoanDiscoveryRequest request = new LoanDiscoveryRequest(null, null, null, null, null,
                "Need an affordable used car loan");

        LoanRecommendationResponse response = service.recommend(request);

        assertThat(response.recommendations()).isEmpty();
        assertThat(response.loanAmount()).isNull();
        assertThat(response.clarifyingQuestion()).contains("requestedAmount");
        verify(repository, never()).findActiveProducts(any(), any(), any());
    }

    private LoanDiscoveryRequest request(String type, String amount, String income, String maxEmi, Integer tenure) {
        return new LoanDiscoveryRequest(type, new BigDecimal(amount),
                income == null ? null : new BigDecimal(income),
                maxEmi == null ? null : new BigDecimal(maxEmi), tenure, null);
    }

    private LoanProduct product(String name, String type, boolean active, String minRate, String maxRate,
                                int minTenure, int maxTenure, String minIncome) {
        LoanProduct product = new LoanProduct(name.replace(" ", "-").toUpperCase(), name, type, null,
                new BigDecimal("0"), new BigDecimal("1000000"),
                minRate == null ? null : new BigDecimal(minRate),
                maxRate == null ? null : new BigDecimal(maxRate), minTenure, maxTenure,
                minIncome == null ? null : new BigDecimal(minIncome), active);
        ReflectionTestUtils.setField(product, "id", UUID.randomUUID());
        return product;
    }

    private void stubCatalogue(LoanProduct... products) {
        List<LoanProduct> catalogue = new ArrayList<>(List.of(products));
        when(repository.findActiveProducts(any(), any(), any())).thenAnswer(invocation -> {
            String type = invocation.getArgument(0);
            BigDecimal amount = invocation.getArgument(1);
            Pageable pageable = invocation.getArgument(2);
            List<LoanProduct> matching = catalogue.stream().filter(LoanProduct::isActive)
                    .filter(product -> type == null || product.getLoanType().equalsIgnoreCase(type))
                    .filter(product -> amount == null || (product.getMinimumAmount().compareTo(amount) <= 0
                            && product.getMaximumAmount().compareTo(amount) >= 0))
                    .sorted(java.util.Comparator.comparing(LoanProduct::getProductName)
                            .thenComparing(LoanProduct::getId))
                    .toList();
            int start = Math.toIntExact(pageable.getOffset());
            int end = Math.min(start + pageable.getPageSize(), matching.size());
            List<LoanProduct> page = start >= end ? List.of() : matching.subList(start, end);
            return new PageImpl<>(page, pageable, matching.size());
        });
    }
}
