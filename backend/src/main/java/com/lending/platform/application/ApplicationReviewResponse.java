package com.lending.platform.application;

import java.util.List;
import java.util.UUID;

public record ApplicationReviewResponse(UUID applicationId, String applicationReference, String recommendation,
                                        List<String> missingDocuments, List<String> nextSteps,
                                        boolean humanApprovalRequired, java.util.UUID auditReference,
                                        List<AgentCheckResponse> checks,
                                        List<String> automatedFindings,
                                        String humanDecision, String humanReviewer,
                                        String humanNote, java.time.Instant humanDecisionAt,
                                        List<String> requestedDocuments) {}
