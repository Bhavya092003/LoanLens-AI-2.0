package com.lending.platform.discovery;

public interface LoanRequirementInterpreter {
    RequirementInterpretation interpret(LoanDiscoveryRequest request);
}
