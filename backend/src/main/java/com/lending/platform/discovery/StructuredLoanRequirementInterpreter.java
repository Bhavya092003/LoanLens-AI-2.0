package com.lending.platform.discovery;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Uses only validated structured fields; no LLM provider is configured in this phase. */
@Component
public class StructuredLoanRequirementInterpreter implements LoanRequirementInterpreter {
    @Override
    public RequirementInterpretation interpret(LoanDiscoveryRequest request) {
        boolean hasNaturalLanguage = request.naturalLanguageRequirements() != null
                && !request.naturalLanguageRequirements().isBlank();
        if (request.requestedAmount() == null) {
            return new RequirementInterpretation(request.loanPurpose(), null, request.monthlyIncome(),
                    request.preferredMaximumEmi(), request.preferredTenureMonths(),
                    "Natural-language requirements were not interpreted because no LLM provider is configured.",
                    "Please enter the requested loan amount in the structured requestedAmount field. "
                            + "Natural-language interpretation is not configured.");
        }

        List<String> criteria = new ArrayList<>();
        criteria.add("amount INR " + request.requestedAmount().toPlainString());
        if (request.loanPurpose() != null && !request.loanPurpose().isBlank()) {
            criteria.add("loan type " + request.loanPurpose().trim());
        }
        if (request.monthlyIncome() != null) {
            criteria.add("monthly income INR " + request.monthlyIncome().toPlainString());
        }
        if (request.preferredMaximumEmi() != null) {
            criteria.add("preferred maximum EMI INR " + request.preferredMaximumEmi().toPlainString());
        }
        if (request.preferredTenureMonths() != null) {
            criteria.add("preferred tenure " + request.preferredTenureMonths() + " months");
        }
        String summary = "Structured criteria: " + String.join(", ", criteria) + ".";
        String clarification = hasNaturalLanguage
                ? "Natural-language text was not interpreted; recommendations use only the structured fields. "
                        + "Please confirm those fields match your request."
                : null;
        return new RequirementInterpretation(request.loanPurpose(), request.requestedAmount(), request.monthlyIncome(),
                request.preferredMaximumEmi(), request.preferredTenureMonths(), summary, clarification);
    }
}
