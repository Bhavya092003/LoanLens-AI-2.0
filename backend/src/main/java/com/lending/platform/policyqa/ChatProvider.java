package com.lending.platform.policyqa;

public interface ChatProvider {
    String answer(String systemPrompt, String userPrompt);
}
