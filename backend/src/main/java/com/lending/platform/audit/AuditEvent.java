package com.lending.platform.audit;

import com.lending.platform.application.LoanApplication;
import com.lending.platform.common.persistence.UuidEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Map;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "audit_events")
public class AuditEvent extends UuidEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id")
    private LoanApplication application;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 24)
    private ActorType actorType;

    @Size(max = 160)
    @Column(name = "actor_reference", length = 160)
    private String actorReference;

    @NotBlank @Size(max = 120)
    @Column(name = "event_type", nullable = false, length = 120)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "event_details", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> eventDetails;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditEvent() {}

    public AuditEvent(LoanApplication application, ActorType actorType, String actorReference,
                      String eventType, Map<String, Object> eventDetails) {
        this.application = application;
        this.actorType = actorType;
        this.actorReference = actorReference;
        this.eventType = eventType;
        this.eventDetails = eventDetails == null ? Map.of() : Map.copyOf(eventDetails);
    }

    public LoanApplication getApplication() { return application; }
    public ActorType getActorType() { return actorType; }
    public String getActorReference() { return actorReference; }
    public String getEventType() { return eventType; }
    public Map<String, Object> getEventDetails() { return eventDetails; }
    public Instant getCreatedAt() { return createdAt; }
}
