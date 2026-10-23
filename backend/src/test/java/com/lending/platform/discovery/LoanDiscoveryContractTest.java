package com.lending.platform.discovery;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import static org.mockito.Mockito.when;

@WebMvcTest(LoanDiscoveryController.class)
@AutoConfigureMockMvc(addFilters = false)
class LoanDiscoveryContractTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmiCalculationService emiCalculationService;

    @MockitoBean
    private LoanRecommendationService loanRecommendationService;

    @Test
    void validStructuredRecommendationRequestUsesResponseContract() throws Exception {
        when(loanRecommendationService.recommend(org.mockito.ArgumentMatchers.any())).thenReturn(
                new LoanRecommendationResponse("Structured criteria: amount INR 500000.", new BigDecimal("500000"),
                        new BigDecimal("60000"), new BigDecimal("12000"), "INR",
                        List.of(new LoanRecommendationResponse.Recommendation(UUID.randomUUID(), "Illustrative fixture",
                                new BigDecimal("12.0"), 60, new BigDecimal("11122.22"),
                                "Test fixture; not an offer.", EligibilityIndicator.LIKELY_ELIGIBLE)),
                        "Indicative only.", null));
        mockMvc.perform(post("/api/v1/loan-discovery/recommendations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestedAmount\":500000,\"loanPurpose\":\"AUTO\","
                                + "\"monthlyIncome\":60000,\"preferredMaximumEmi\":12000,"
                                + "\"preferredTenureMonths\":60}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.recommendations[0].productName").value("Illustrative fixture"))
                .andExpect(jsonPath("$.recommendations[0].eligibilityIndicator").value("LIKELY_ELIGIBLE"));
    }

    @Test
    void invalidRequestUsesValidationErrorEnvelope() throws Exception {
        mockMvc.perform(post("/api/v1/loan-discovery/emi")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loanAmount\":0,\"annualInterestRate\":5,\"tenureMonths\":12}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }

    @Test
    void recommendationRequestWithoutStructuredAmountOrTextIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/loan-discovery/recommendations")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void validEmiRequestReturnsCalculationContract() throws Exception {
        when(emiCalculationService.calculate(org.mockito.ArgumentMatchers.any())).thenReturn(
                new EmiResponse(new BigDecimal("100000.00"), new BigDecimal("12.0"), 12,
                        new BigDecimal("8884.88"), new BigDecimal("106618.56"),
                        new BigDecimal("6618.56"), "INR"));

        mockMvc.perform(post("/api/v1/loan-discovery/emi")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loanAmount\":100000,\"annualInterestRate\":12,\"tenureMonths\":12}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estimatedEmi").value(8884.88))
                .andExpect(jsonPath("$.totalAmountPayable").value(106618.56))
                .andExpect(jsonPath("$.totalInterestPayable").value(6618.56))
                .andExpect(jsonPath("$.currency").value("INR"));
    }

    @Test
    void invalidPrincipalRateAndTenureAreRejected() throws Exception {
        List<String> invalidRequests = List.of(
                "{\"loanAmount\":0,\"annualInterestRate\":5,\"tenureMonths\":12}",
                "{\"loanAmount\":-1,\"annualInterestRate\":5,\"tenureMonths\":12}",
                "{\"loanAmount\":100,\"annualInterestRate\":-0.1,\"tenureMonths\":12}",
                "{\"loanAmount\":100,\"annualInterestRate\":5,\"tenureMonths\":0}",
                "{\"loanAmount\":100,\"annualInterestRate\":5}");

        for (String request : invalidRequests) {
            mockMvc.perform(post("/api/v1/loan-discovery/emi")
                            .contentType(MediaType.APPLICATION_JSON).content(request))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
        }
    }
}
