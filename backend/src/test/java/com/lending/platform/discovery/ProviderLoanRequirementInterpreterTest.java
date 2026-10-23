package com.lending.platform.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lending.platform.policyqa.ChatProvider;
import org.junit.jupiter.api.Test;

class ProviderLoanRequirementInterpreterTest {
    private final ChatProvider chat = mock(ChatProvider.class);
    private final ProviderLoanRequirementInterpreter interpreter = new ProviderLoanRequirementInterpreter(chat, new ObjectMapper());

    @Test
    void acceptsOnlyBoundedStructuredCriteriaAndPreservesExplicitFields() {
        when(chat.answer(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("{\"loanPurpose\":\"HOME\",\"requestedAmount\":250000,\"monthlyIncome\":50000,\"preferredMaximumEmi\":12000,\"preferredTenureMonths\":36}");
        RequirementInterpretation result = interpreter.interpret(new LoanDiscoveryRequest(null, null, null, null, null, "I need a home loan"));
        assertThat(result.requestedAmount()).isEqualByComparingTo("250000");
        assertThat(result.loanPurpose()).isEqualTo("HOME");
        assertThat(result.preferredTenureMonths()).isEqualTo(36);
        assertThat(result.clarification()).isNull();
    }

    @Test
    void malformedOrOutOfRangeProviderOutputDoesNotCreateRequirements() {
        when(chat.answer(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("{\"requestedAmount\":999999999999999999}");
        RequirementInterpretation result = interpreter.interpret(new LoanDiscoveryRequest(null, null, null, null, null, "Need a loan"));
        assertThat(result.requestedAmount()).isNull();
        assertThat(result.clarification()).contains("AI interpretation is unavailable");
    }
}
