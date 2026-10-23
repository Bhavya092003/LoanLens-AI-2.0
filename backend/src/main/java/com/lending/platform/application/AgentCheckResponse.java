package com.lending.platform.application;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AgentCheckResponse(UUID checkId, UUID applicationId, AgentType agentType,
                                 CheckStatus checkStatus, Map<String, Object> resultDetails,
                                 boolean requiresHumanReview, Instant createdAt) {
    public static AgentCheckResponse from(AgentCheck check) {
        return new AgentCheckResponse(check.getId(), check.getApplication().getId(), check.getAgentType(),
                check.getCheckStatus(), check.getResultDetails(), check.isRequiresHumanReview(), check.getCreatedAt());
    }
}
