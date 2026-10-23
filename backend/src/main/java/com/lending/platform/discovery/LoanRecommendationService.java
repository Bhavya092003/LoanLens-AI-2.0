package com.lending.platform.discovery;

import com.lending.platform.loanproduct.LoanProduct;
import com.lending.platform.loanproduct.LoanProductRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoanRecommendationService {
    private static final int CANDIDATE_PAGE_SIZE = 100;
    private static final String DISCLAIMER = "Indicative catalogue matches only. This is not an approval, "
            + "verified eligibility, or a lender offer. Final terms are subject to lender verification and assessment.";

    private final LoanRequirementInterpreter requirementInterpreter;
    private final LoanProductRepository products;
    private final EmiCalculationService emiCalculationService;

    public LoanRecommendationService(LoanRequirementInterpreter requirementInterpreter,
                                    LoanProductRepository products,
                                    EmiCalculationService emiCalculationService) {
        this.requirementInterpreter = requirementInterpreter;
        this.products = products;
        this.emiCalculationService = emiCalculationService;
    }

    @Transactional(readOnly = true)
    public LoanRecommendationResponse recommend(LoanDiscoveryRequest request) {
        RequirementInterpretation requirement = requirementInterpreter.interpret(request);
        if (requirement.requestedAmount() == null) {
            return response(requirement, List.of(), requirement.clarification());
        }

        List<LoanProduct> candidates = findCandidates(requirement);
        List<RankedRecommendation> ranked = candidates.stream()
                .filter(LoanProduct::isActive)
                .filter(product -> meetsKnownIncomeThreshold(product, requirement.monthlyIncome()))
                .map(product -> evaluate(product, requirement))
                .sorted(rankingOrder(requirement))
                .toList();

        List<LoanRecommendationResponse.Recommendation> recommendations = ranked.stream()
                .map(RankedRecommendation::recommendation)
                .toList();
        String clarification = requirement.clarification();
        if (recommendations.isEmpty() && clarification == null) {
            clarification = candidates.isEmpty()
                    ? "No active catalogue products match the supplied loan type and amount. "
                            + "Review the request or ask the catalogue administrator to verify available product data."
                    : "No active catalogue products meet the supplied loan amount, type, and known minimum-income requirements. "
                            + "Review the structured request or available catalogue data.";
        }
        return response(requirement, recommendations, clarification);
    }

    private List<LoanProduct> findCandidates(RequirementInterpretation requirement) {
        List<LoanProduct> candidates = new ArrayList<>();
        int pageNumber = 0;
        Page<LoanProduct> page;
        do {
            PageRequest pageRequest = PageRequest.of(pageNumber++, CANDIDATE_PAGE_SIZE,
                    Sort.by(Sort.Order.asc("productName"), Sort.Order.asc("id")));
            page = products.findActiveProducts(requirement.loanPurpose(), requirement.requestedAmount(), pageRequest);
            candidates.addAll(page.getContent());
        } while (page.hasNext());
        return candidates;
    }

    private boolean meetsKnownIncomeThreshold(LoanProduct product, BigDecimal monthlyIncome) {
        BigDecimal threshold = product.getMinimumMonthlyIncome();
        return threshold == null || monthlyIncome == null || monthlyIncome.compareTo(threshold) >= 0;
    }

    private RankedRecommendation evaluate(LoanProduct product, RequirementInterpretation requirement) {
        BigDecimal minimumRate = product.getMinimumAnnualInterestRate();
        BigDecimal maximumRate = product.getMaximumAnnualInterestRate();
        if (minimumRate == null || maximumRate == null || minimumRate.signum() < 0
                || maximumRate.compareTo(minimumRate) < 0 || maximumRate.compareTo(BigDecimal.valueOf(100)) > 0) {
            return uncalculable(product, requirement);
        }

        // Use the stored upper bound as a conservative scenario, never as a lender offer.
        BigDecimal scenarioRate = maximumRate;
        InstallmentOption option = selectTenure(product, requirement, scenarioRate);
        if (option == null) {
            return uncalculable(product, requirement);
        }

        BigDecimal emi = option.response().estimatedEmi();
        boolean budgetMet = requirement.preferredMaximumEmi() == null
                || emi.compareTo(requirement.preferredMaximumEmi()) <= 0;
        boolean incomeKnown = requirement.monthlyIncome() != null;
        boolean incomeCanCoverEmi = incomeKnown && emi.compareTo(requirement.monthlyIncome()) <= 0;
        EligibilityIndicator indicator = !incomeKnown || product.getMinimumMonthlyIncome() == null
                ? EligibilityIndicator.INSUFFICIENT_INFORMATION
                : (budgetMet && incomeCanCoverEmi ? EligibilityIndicator.LIKELY_ELIGIBLE
                        : EligibilityIndicator.MAY_BE_ELIGIBLE);

        String reason = reason(product, requirement, option, budgetMet, incomeKnown, incomeCanCoverEmi);
        var recommendation = new LoanRecommendationResponse.Recommendation(product.getId(), product.getProductName(),
                scenarioRate, option.response().tenureMonths(), emi, reason, indicator);
        return new RankedRecommendation(recommendation, budgetMet, incomeKnown && incomeCanCoverEmi,
                option.preferredTenureDistance(), emi, product.getProductName(), product.getId());
    }

    private RankedRecommendation uncalculable(LoanProduct product, RequirementInterpretation requirement) {
        String reason = "The product does not contain a usable stored interest-rate range, so no EMI was calculated.";
        var recommendation = new LoanRecommendationResponse.Recommendation(product.getId(), product.getProductName(),
                null, product.getMinimumTenureMonths(), null, reason, EligibilityIndicator.INSUFFICIENT_INFORMATION);
        return new RankedRecommendation(recommendation, false, false, Integer.MAX_VALUE,
                null, product.getProductName(), product.getId());
    }

    private InstallmentOption selectTenure(LoanProduct product, RequirementInterpretation requirement,
                                            BigDecimal scenarioRate) {
        if (product.getMinimumTenureMonths() < 1
                || product.getMaximumTenureMonths() < product.getMinimumTenureMonths()
                || product.getMaximumTenureMonths() > 600) {
            return null;
        }
        InstallmentOption best = null;
        for (int months = product.getMinimumTenureMonths(); months <= product.getMaximumTenureMonths(); months++) {
            EmiResponse emi = emiCalculationService.calculate(new EmiRequest(
                    requirement.requestedAmount(), scenarioRate, months));
            int distance = requirement.preferredTenureMonths() == null
                    ? 0 : Math.abs(months - requirement.preferredTenureMonths());
            InstallmentOption current = new InstallmentOption(emi, distance);
            if (best == null || tenureOrder(requirement).compare(current, best) < 0) {
                best = current;
            }
        }
        return best;
    }

    private Comparator<InstallmentOption> tenureOrder(RequirementInterpretation requirement) {
        Comparator<InstallmentOption> comparator = Comparator.comparing(
                option -> requirement.preferredMaximumEmi() == null
                        || option.response().estimatedEmi().compareTo(requirement.preferredMaximumEmi()) <= 0,
                Comparator.reverseOrder());
        if (requirement.preferredTenureMonths() != null) {
            comparator = comparator.thenComparingInt(InstallmentOption::preferredTenureDistance);
        } else {
            // With no stated tenure preference, select the lowest EMI among terms with equal budget fit.
            comparator = comparator.thenComparing(option -> option.response().estimatedEmi());
        }
        return comparator.thenComparingInt(option -> option.response().tenureMonths());
    }

    private Comparator<RankedRecommendation> rankingOrder(RequirementInterpretation requirement) {
        Comparator<RankedRecommendation> comparator = Comparator
                .comparing(RankedRecommendation::budgetMet, Comparator.reverseOrder())
                .thenComparing(RankedRecommendation::incomeCanCoverEmi, Comparator.reverseOrder());
        if (requirement.preferredTenureMonths() != null) {
            comparator = comparator.thenComparingInt(RankedRecommendation::preferredTenureDistance);
        }
        return comparator.thenComparing(RankedRecommendation::estimatedEmi,
                        Comparator.nullsLast(BigDecimal::compareTo))
                .thenComparing(RankedRecommendation::productName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(RankedRecommendation::productId, Comparator.nullsLast(UUID::compareTo));
    }

    private String reason(LoanProduct product, RequirementInterpretation requirement,
                          InstallmentOption option, boolean budgetMet,
                          boolean incomeKnown, boolean incomeCanCoverEmi) {
        List<String> details = new ArrayList<>();
        details.add("Estimated using the stored maximum annual interest rate of "
                + product.getMaximumAnnualInterestRate().toPlainString()
                + "% as a conservative scenario, not as an offer.");
        if (requirement.preferredTenureMonths() != null
                && option.response().tenureMonths() == requirement.preferredTenureMonths()) {
            details.add("The preferred tenure is supported.");
        } else if (requirement.preferredTenureMonths() != null) {
            details.add("The selected supported tenure is closest to the preferred tenure while prioritizing the EMI budget.");
        } else {
            details.add("The selected supported tenure produces the lowest estimated EMI among this product's terms.");
        }
        if (requirement.preferredMaximumEmi() != null) {
            details.add(budgetMet ? "The estimated EMI is within the stated maximum."
                    : "The estimated EMI exceeds the stated maximum.");
        }
        if (incomeKnown) {
            details.add(incomeCanCoverEmi
                    ? "The estimated EMI does not exceed reported monthly income; this is only an affordability signal."
                    : "The estimated EMI exceeds reported monthly income; additional affordability review is needed.");
            if (product.getMinimumMonthlyIncome() != null) {
                details.add("Reported income meets the catalogue's stated minimum-income threshold.");
            }
        } else {
            details.add("Income affordability cannot be evaluated because monthly income was not provided.");
        }
        if (product.getMinimumMonthlyIncome() == null) {
            details.add("No minimum-income threshold is recorded for this product.");
        }
        details.add("Catalogue matching is not a credit assessment or approval.");
        return String.join(" ", details);
    }

    private LoanRecommendationResponse response(RequirementInterpretation requirement,
            List<LoanRecommendationResponse.Recommendation> recommendations, String clarification) {
        return new LoanRecommendationResponse(requirement.interpretedRequirement(), requirement.requestedAmount(),
                requirement.monthlyIncome(), requirement.preferredMaximumEmi(), "INR", recommendations,
                DISCLAIMER, clarification);
    }

    private record InstallmentOption(EmiResponse response, int preferredTenureDistance) {}

    private record RankedRecommendation(LoanRecommendationResponse.Recommendation recommendation,
                                        boolean budgetMet, boolean incomeCanCoverEmi,
                                        int preferredTenureDistance, BigDecimal estimatedEmi,
                                        String productName, UUID productId) {}
}
