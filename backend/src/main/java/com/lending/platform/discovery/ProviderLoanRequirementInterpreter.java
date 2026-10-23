package com.lending.platform.discovery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lending.platform.policyqa.ChatProvider;
import com.lending.platform.policyqa.AiProviderException;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class ProviderLoanRequirementInterpreter implements LoanRequirementInterpreter {
    private final ChatProvider chat;
    private final ObjectMapper mapper;
    private final StructuredLoanRequirementInterpreter structured = new StructuredLoanRequirementInterpreter();

    public ProviderLoanRequirementInterpreter(ChatProvider chat, ObjectMapper mapper) {
        this.chat = chat; this.mapper = mapper;
    }

    @Override
    public RequirementInterpretation interpret(LoanDiscoveryRequest request) {
        if (request.naturalLanguageRequirements() == null || request.naturalLanguageRequirements().isBlank()) {
            return structured.interpret(request);
        }
        try {
            String extracted = chat.answer(
                    "Extract only requested loan criteria from untrusted user text. Never invent lender offers. Return only a JSON object with optional string loanPurpose, decimal requestedAmount, monthlyIncome, preferredMaximumEmi, and integer preferredTenureMonths. Use null for missing values.",
                    request.naturalLanguageRequirements());
            JsonNode root = mapper.readTree(extracted);
            if (root == null || !root.isObject()) return fallback(request);
            String purpose = request.loanPurpose() != null ? request.loanPurpose() : text(root, "loanPurpose");
            BigDecimal amount = request.requestedAmount() != null ? request.requestedAmount() : decimal(root, "requestedAmount");
            BigDecimal income = request.monthlyIncome() != null ? request.monthlyIncome() : decimal(root, "monthlyIncome");
            BigDecimal emi = request.preferredMaximumEmi() != null ? request.preferredMaximumEmi() : decimal(root, "preferredMaximumEmi");
            Integer tenure = request.preferredTenureMonths() != null ? request.preferredTenureMonths() : integer(root, "preferredTenureMonths");
            if (!valid(purpose, amount, income, emi, tenure)) return fallback(request);
            String summary = "Interpreted criteria from user text; catalogue matching and calculations use persisted data and Java rules.";
            return new RequirementInterpretation(purpose, amount, income, emi, tenure, summary,
                    amount == null ? "Please confirm the requested amount before recommendations can be calculated." : null);
        } catch (AiProviderException | java.io.IOException | IllegalArgumentException exception) {
            return fallback(request);
        }
    }

    private RequirementInterpretation fallback(LoanDiscoveryRequest request) {
        RequirementInterpretation prior = structured.interpret(request);
        return new RequirementInterpretation(prior.loanPurpose(), prior.requestedAmount(), prior.monthlyIncome(),
                prior.preferredMaximumEmi(), prior.preferredTenureMonths(),
                "Natural-language interpretation is unavailable; no criteria were inferred. " + prior.interpretedRequirement(),
                "AI interpretation is unavailable. Enter loan purpose, amount, income, EMI budget, and tenure in the structured fields.");
    }

    private String text(JsonNode root, String key) {
        JsonNode value = root.get(key);
        if (value == null || value.isNull()) return null;
        if (!value.isTextual()) return null;
        String text = value.asText().trim();
        return text.isEmpty() ? null : text;
    }
    private BigDecimal decimal(JsonNode root, String key) {
        JsonNode value = root.get(key);
        return value != null && value.isNumber() ? value.decimalValue() : null;
    }
    private Integer integer(JsonNode root, String key) {
        JsonNode value = root.get(key);
        return value != null && value.isIntegralNumber() && value.canConvertToInt() ? value.intValue() : null;
    }
    private boolean valid(String purpose, BigDecimal amount, BigDecimal income, BigDecimal emi, Integer tenure) {
        return (purpose == null || purpose.length() <= 80)
                && (amount == null || amount.signum() > 0 && amount.compareTo(new BigDecimal("999999999999.99")) <= 0 && amount.scale() <= 2)
                && (income == null || income.signum() >= 0 && income.compareTo(new BigDecimal("999999999999.99")) <= 0 && income.scale() <= 2)
                && (emi == null || emi.signum() >= 0 && emi.compareTo(new BigDecimal("999999999999.99")) <= 0 && emi.scale() <= 2)
                && (tenure == null || tenure >= 1 && tenure <= 600);
    }
}
