package com.lending.platform.application;

import com.lending.platform.common.persistence.UuidEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "agent_checks")
public class AgentCheck extends UuidEntity {
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private LoanApplication application;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "agent_type", nullable = false, length = 32)
    private AgentType agentType;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "check_status", nullable = false, length = 32)
    private CheckStatus checkStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_details", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> resultDetails;

    @Column(name = "requires_human_review", nullable = false)
    private boolean requiresHumanReview;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AgentCheck() {}

    public AgentCheck(LoanApplication application, AgentType agentType, CheckStatus checkStatus,
                      Map<String, Object> resultDetails, boolean requiresHumanReview) {
        this.application = application;
        this.agentType = agentType;
        this.checkStatus = checkStatus;
        this.resultDetails = resultDetails == null ? Map.of() : Map.copyOf(resultDetails);
        this.requiresHumanReview = requiresHumanReview;
    }

    public LoanApplication getApplication() { return application; }
    public AgentType getAgentType() { return agentType; }
    public CheckStatus getCheckStatus() { return checkStatus; }
    public Map<String, Object> getResultDetails() { return resultDetails; }
    public boolean isRequiresHumanReview() { return requiresHumanReview; }
    public Instant getCreatedAt() { return createdAt; }
}
