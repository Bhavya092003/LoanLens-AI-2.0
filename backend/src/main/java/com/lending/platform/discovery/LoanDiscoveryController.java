package com.lending.platform.discovery;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/loan-discovery")
public class LoanDiscoveryController {
    private final EmiCalculationService emiCalculationService;
    private final LoanRecommendationService loanRecommendationService;

    public LoanDiscoveryController(EmiCalculationService emiCalculationService,
                                   LoanRecommendationService loanRecommendationService) {
        this.emiCalculationService = emiCalculationService;
        this.loanRecommendationService = loanRecommendationService;
    }

    @PostMapping("/recommendations")
    public LoanRecommendationResponse recommendations(@Valid @RequestBody LoanDiscoveryRequest request) {
        return loanRecommendationService.recommend(request);
    }

    @PostMapping("/emi")
    public EmiResponse calculateEmi(@Valid @RequestBody EmiRequest request) {
        return emiCalculationService.calculate(request);
    }
}
