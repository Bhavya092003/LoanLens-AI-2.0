package com.lending.platform.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class StructuredLoanRequirementInterpreterTest {
    private final StructuredLoanRequirementInterpreter interpreter = new StructuredLoanRequirementInterpreter();

    @Test
    void usesExplicitStructuredFieldsWithoutClaimingNaturalLanguageWasInterpreted() {
        var result = interpreter.interpret(new LoanDiscoveryRequest("AUTO", new BigDecimal("500000"),
                new BigDecimal("60000"), new BigDecimal("12000"), 60,
                "I need a used car loan for five lakh"));

        assertThat(result.requestedAmount()).isEqualByComparingTo("500000");
        assertThat(result.loanPurpose()).isEqualTo("AUTO");
        assertThat(result.interpretedRequirement()).contains("Structured criteria");
        assertThat(result.clarification()).contains("Natural-language text was not interpreted");
    }

    @Test
    void asksForStructuredAmountWhenOnlyAmbiguousNaturalLanguageWasProvided() {
        var result = interpreter.interpret(new LoanDiscoveryRequest(null, null, null, null, null,
                "I need an affordable loan soon"));

        assertThat(result.requestedAmount()).isNull();
        assertThat(result.clarification()).contains("requestedAmount");
        assertThat(result.interpretedRequirement()).contains("no LLM provider is configured");
    }
}
