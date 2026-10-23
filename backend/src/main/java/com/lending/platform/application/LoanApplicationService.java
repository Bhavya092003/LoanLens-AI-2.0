package com.lending.platform.application;

import com.lending.platform.common.api.ApiException;
import com.lending.platform.common.api.PageResponse;
import com.lending.platform.common.api.ResourceNotFoundException;
import com.lending.platform.audit.ActorType;
import com.lending.platform.audit.AuditEvent;
import com.lending.platform.audit.AuditEventRepository;
import com.lending.platform.discovery.EmiCalculationService;
import com.lending.platform.discovery.EmiRequest;
import com.lending.platform.loanproduct.LoanProduct;
import com.lending.platform.loanproduct.LoanProductRepository;
import com.lending.platform.user.CustomerUser;
import com.lending.platform.user.CustomerUserRepository;
import java.util.Locale;
import java.util.UUID;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.unit.DataSize;
import org.springframework.beans.factory.annotation.Value;

@Service
public class LoanApplicationService {
    private final LoanApplicationRepository applications;
    private final CustomerUserRepository users;
    private final LoanProductRepository products;
    private final ApplicationStatusHistoryRepository statusHistory;
    private final ApplicationDocumentRepository documents;
    private final AgentCheckRepository checks;
    private final AuditEventRepository auditEvents;
    private final ApplicationFileStorage fileStorage;
    private final EmiCalculationService emi;
    private final long maxApplicationFileSizeBytes;

    public LoanApplicationService(LoanApplicationRepository applications, CustomerUserRepository users,
                                  LoanProductRepository products, ApplicationStatusHistoryRepository statusHistory,
                                  ApplicationDocumentRepository documents, AgentCheckRepository checks,
                                  AuditEventRepository auditEvents, ApplicationFileStorage fileStorage,
                                  EmiCalculationService emi,
                                  @Value("${app.application-storage.max-file-size:10MB}") DataSize maxApplicationFileSize) {
        this.applications = applications;
        this.users = users;
        this.products = products;
        this.statusHistory = statusHistory;
        this.documents = documents;
        this.checks = checks;
        this.auditEvents = auditEvents;
        this.fileStorage = fileStorage;
        this.emi = emi;
        this.maxApplicationFileSizeBytes = maxApplicationFileSize.toBytes();
    }

    @Transactional
    public LoanApplicationResponse create(CreateApplicationRequest request) {
        CustomerUser user = users.findById(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User"));
        LoanProduct product = request.loanProductId() == null ? null : products.findById(request.loanProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Loan product"));
        if (product != null) {
            if (!product.isActive()) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PRODUCT_INACTIVE", "The selected loan product is inactive");
            if (request.requestedAmount().compareTo(product.getMinimumAmount()) < 0 || request.requestedAmount().compareTo(product.getMaximumAmount()) > 0)
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "AMOUNT_OUTSIDE_PRODUCT_RANGE", "Requested amount is outside the selected product range");
            if (request.requestedTenureMonths() == null || request.requestedTenureMonths() < product.getMinimumTenureMonths() || request.requestedTenureMonths() > product.getMaximumTenureMonths())
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "TENURE_OUTSIDE_PRODUCT_RANGE", "Requested tenure is outside the selected product range");
        }
        String reference = "LA-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase(Locale.ROOT);
        LoanApplication application = applications.saveAndFlush(new LoanApplication(reference, user, product,
                request.requestedAmount(), request.requestedTenureMonths(), request.monthlyIncome(), ApplicationStatus.DRAFT));
        statusHistory.save(new ApplicationStatusHistory(application, null, ApplicationStatus.DRAFT, null,
                "Application created"));
        auditEvents.save(new AuditEvent(application, ActorType.SYSTEM, null, "APPLICATION_CREATED",
                Map.of("applicationReference", reference, "loanProductSelected", product != null)));
        return LoanApplicationResponse.from(application);
    }

    @Transactional
    public ApplicationDocumentResponse uploadDocument(UUID applicationId, String documentType, MultipartFile file) {
        LoanApplication application = applications.findByIdForUpdate(applicationId).orElseThrow(() -> new ResourceNotFoundException("Loan application"));
        if (file == null || file.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_FILE", "A non-empty document is required");
        if (file.getSize() > maxApplicationFileSizeBytes) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "Document exceeds the configured size limit");
        byte[] bytes;
        try { bytes = file.getBytes(); } catch (IOException exception) { throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FILE", "The uploaded document could not be read"); }
        if (bytes.length > maxApplicationFileSizeBytes) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "Document exceeds the configured size limit");
        String extension = fileStorage.extension(bytes);
        String normalizedType = documentType.trim().toUpperCase(Locale.ROOT);
        String sha256 = HexFormat.of().formatHex(sha256(bytes));
        var duplicate = documents.findFirstByApplication_IdAndDocumentTypeAndContentSha256(applicationId, normalizedType, sha256);
        if (duplicate.isPresent()) return ApplicationDocumentResponse.from(duplicate.get());
        String filename = file.getOriginalFilename() == null ? "document." + extension : file.getOriginalFilename().replace('\\', '/');
        filename = filename.substring(filename.lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9._ -]", "_");
        if (filename.isBlank()) filename = "document." + extension;
        String contentType = switch (extension) { case "pdf" -> "application/pdf"; case "png" -> "image/png"; default -> "image/jpeg"; };
        String storageKey = fileStorage.store(bytes);
        ApplicationDocument document = documents.saveAndFlush(new ApplicationDocument(application, normalizedType,
                "filesystem", storageKey, filename, contentType, (long) bytes.length, sha256, DocumentStatus.UPLOADED, Instant.now()));
        auditEvents.save(new AuditEvent(application, ActorType.SYSTEM, null, "APPLICATION_DOCUMENT_UPLOADED",
                Map.of("documentId", document.getId().toString(), "documentType", document.getDocumentType(), "fileSizeBytes", bytes.length)));
        return ApplicationDocumentResponse.from(document);
    }

    @Transactional
    public LoanApplicationResponse submit(UUID applicationId) {
        LoanApplication application = applications.findByIdForUpdate(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Loan application"));
        if (application.getApplicationStatus() != ApplicationStatus.DRAFT) {
            throw new ApiException(HttpStatus.CONFLICT, "APPLICATION_NOT_SUBMITTABLE",
                    "Only draft applications can be submitted");
        }
        ApplicationStatus before = application.getApplicationStatus();
        application.setApplicationStatus(ApplicationStatus.SUBMITTED);
        statusHistory.save(new ApplicationStatusHistory(application, before, ApplicationStatus.SUBMITTED, null,
                "Application submitted for human review"));
        auditEvents.save(new AuditEvent(application, ActorType.SYSTEM, null, "APPLICATION_SUBMITTED",
                Map.of("applicationReference", application.getApplicationReference())));
        return LoanApplicationResponse.from(application);
    }

    private byte[] sha256(byte[] content) {
        try { return MessageDigest.getInstance("SHA-256").digest(content); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 is unavailable", exception); }
    }

    @Transactional
    public ApplicationReviewResponse review(UUID applicationId, ReviewRequest request) {
        LoanApplication application = applications.findByIdForUpdate(applicationId).orElseThrow(() -> new ResourceNotFoundException("Loan application"));
        var prior = checks.findByApplication_IdOrderByCreatedAtDesc(applicationId, PageRequest.of(0, 3));
        if (prior.hasContent()) {
            List<String> missingBefore = prior.getContent().stream().filter(c -> c.getAgentType() == AgentType.DOCUMENT_VERIFICATION)
                    .flatMap(c -> ((List<?>) c.getResultDetails().getOrDefault("missingDocumentTypes", List.of())).stream()).map(Object::toString).toList();
            boolean resolved = !missingBefore.isEmpty() && missingBefore.stream().allMatch(type ->
                    documents.existsByApplication_IdAndDocumentTypeIgnoreCaseAndDocumentStatus(applicationId, type, DocumentStatus.UPLOADED));
            if (!resolved) return reviewResponse(application, prior.getContent());
        }
        if (!(application.getApplicationStatus() == ApplicationStatus.DRAFT || application.getApplicationStatus() == ApplicationStatus.SUBMITTED
                || application.getApplicationStatus() == ApplicationStatus.ADDITIONAL_DOCUMENTS_REQUIRED))
            throw new ApiException(HttpStatus.CONFLICT, "APPLICATION_NOT_REVIEWABLE", "Application is not in a reviewable state");
        List<String> required = List.of("IDENTITY", "ADDRESS", "INCOME");
        List<String> missing = required.stream().filter(type -> !documents.existsByApplication_IdAndDocumentTypeIgnoreCaseAndDocumentStatus(
                applicationId, type, DocumentStatus.UPLOADED)).toList();
        List<AgentCheck> results = new ArrayList<>();
        Map<String, Object> docDetails = new HashMap<>();
        docDetails.put("requiredDocumentTypes", required); docDetails.put("missingDocumentTypes", missing);
        docDetails.put("verificationPerformed", false);
        results.add(new AgentCheck(application, AgentType.DOCUMENT_VERIFICATION,
                missing.isEmpty() ? CheckStatus.NEEDS_HUMAN_REVIEW : CheckStatus.INCOMPLETE, docDetails, true));
        Map<String, Object> affordability = new HashMap<>();
        affordability.put("declaredIncomeVerified", false); affordability.put("creditBureauChecked", false);
        CheckStatus affordabilityStatus = CheckStatus.INCOMPLETE;
        if (application.getLoanProduct() != null && application.getRequestedTenureMonths() != null
                && application.getMonthlyIncome() != null && application.getMonthlyIncome().signum() > 0) {
            BigDecimal estimate = emi.calculate(new EmiRequest(application.getRequestedAmount(),
                    application.getLoanProduct().getMaximumAnnualInterestRate(), application.getRequestedTenureMonths())).estimatedEmi();
            BigDecimal ratio = estimate.multiply(BigDecimal.valueOf(100)).divide(application.getMonthlyIncome().max(BigDecimal.ONE), 2, java.math.RoundingMode.HALF_UP);
            affordability.put("estimatedEmi", estimate); affordability.put("declaredIncomeMonthly", application.getMonthlyIncome());
            affordability.put("indicativeEmiToIncomePercent", ratio); affordability.put("assumption", "Maximum catalogue annual rate; declared income is unverified");
            affordabilityStatus = CheckStatus.NEEDS_HUMAN_REVIEW;
        } else affordability.put("missingInputs", List.of("active product, tenure, or positive declared monthly income"));
        results.add(new AgentCheck(application, AgentType.CREDIT_ASSESSMENT, affordabilityStatus, affordability, true));
        results.add(new AgentCheck(application, AgentType.COMPLIANCE, CheckStatus.NEEDS_HUMAN_REVIEW,
                Map.of("checksPerformed", List.of(), "unavailableChecks", List.of("KYC", "sanctions screening", "credit bureau", "regulatory compliance")), true));
        List<AgentCheck> saved = checks.saveAllAndFlush(results);
        ApplicationStatus before = application.getApplicationStatus();
        ApplicationStatus after = missing.isEmpty() ? ApplicationStatus.DECISION_PENDING : ApplicationStatus.ADDITIONAL_DOCUMENTS_REQUIRED;
        application.setApplicationStatus(ApplicationStatus.UNDER_REVIEW);
        statusHistory.save(new ApplicationStatusHistory(application, before, ApplicationStatus.UNDER_REVIEW, null,
                "Deterministic review checks started"));
        application.setApplicationStatus(after);
        statusHistory.save(new ApplicationStatusHistory(application, ApplicationStatus.UNDER_REVIEW, after, null,
                "Automated checks completed; human decision required"));
        auditEvents.saveAndFlush(new AuditEvent(application, ActorType.SYSTEM, null, "APPLICATION_REVIEW_COMPLETED",
                Map.of("checkCount", saved.size(), "missingDocumentCount", missing.size(), "humanApprovalRequired", true)));
        return reviewResponse(application, saved);
    }

    @Transactional(readOnly = true)
    public ApplicationReviewResponse getReview(UUID applicationId) {
        LoanApplication application = applications.findById(applicationId).orElseThrow(() -> new ResourceNotFoundException("Loan application"));
        List<AgentCheck> saved = checks.findByApplication_IdOrderByCreatedAtDesc(applicationId, PageRequest.of(0, 3)).getContent();
        return reviewResponse(application, saved);
    }

    @Transactional
    public LoanApplicationResponse decide(UUID applicationId, HumanDecisionRequest request, UUID reviewerId) {
        LoanApplication application = applications.findByIdForUpdate(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Loan application"));
        ApplicationStatus next = switch (request.decision().trim().toUpperCase(Locale.ROOT)) {
            case "APPROVE" -> ApplicationStatus.APPROVED;
            case "DECLINE" -> ApplicationStatus.DECLINED;
            case "REQUEST_DOCUMENTS" -> ApplicationStatus.ADDITIONAL_DOCUMENTS_REQUIRED;
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_HUMAN_DECISION",
                    "Decision must be APPROVE, DECLINE, or REQUEST_DOCUMENTS");
        };
        if (application.getApplicationStatus() != ApplicationStatus.DECISION_PENDING
                && application.getApplicationStatus() != ApplicationStatus.ADDITIONAL_DOCUMENTS_REQUIRED
                && application.getApplicationStatus() != ApplicationStatus.UNDER_REVIEW) {
            throw new ApiException(HttpStatus.CONFLICT, "APPLICATION_NOT_DECIDABLE",
                    "Run the review checks before recording a human decision");
        }
        ApplicationStatus before = application.getApplicationStatus();
        List<String> requestedDocuments = request.requestedDocumentTypes() == null ? List.of()
                : request.requestedDocumentTypes().stream().map(String::trim).filter(value -> !value.isBlank()).toList();
        if (next == ApplicationStatus.ADDITIONAL_DOCUMENTS_REQUIRED && requestedDocuments.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DOCUMENT_TYPES_REQUIRED",
                    "List the document types required from the customer");
        }
        application.setApplicationStatus(next);
        statusHistory.save(new ApplicationStatusHistory(application, before, next, reviewerId,
                request.note() == null || request.note().isBlank() ? "Human reviewer decision recorded" : request.note().trim()));
        auditEvents.saveAndFlush(new AuditEvent(application, ActorType.USER, reviewerId.toString(), "HUMAN_DECISION_RECORDED",
                Map.of("decision", next.name(), "note", request.note() == null ? "" : request.note().trim(),
                        "requestedDocuments", requestedDocuments)));
        return LoanApplicationResponse.from(application);
    }

    @Transactional(readOnly = true)
    public List<LoanApplicationResponse> reviewQueue() {
        return applications.findTop50ByApplicationStatusInOrderByUpdatedAtDesc(
                        List.of(ApplicationStatus.SUBMITTED, ApplicationStatus.UNDER_REVIEW,
                                ApplicationStatus.DECISION_PENDING, ApplicationStatus.ADDITIONAL_DOCUMENTS_REQUIRED))
                .stream().map(LoanApplicationResponse::from).toList();
    }

    private ApplicationReviewResponse reviewResponse(LoanApplication application, List<AgentCheck> saved) {
        List<String> missing = saved.stream().filter(c -> c.getAgentType() == AgentType.DOCUMENT_VERIFICATION)
                .flatMap(c -> ((List<?>) c.getResultDetails().getOrDefault("missingDocumentTypes", List.of())).stream()).map(Object::toString).toList();
        String recommendation = !saved.isEmpty() && !missing.isEmpty() ? "REQUEST_ADDITIONAL_DOCUMENTS"
                : saved.isEmpty() ? "CHECKS_NOT_RUN" : "PENDING_HUMAN_REVIEW";
        UUID auditId = auditEvents.findTopByApplication_IdAndEventTypeOrderByCreatedAtDesc(application.getId(), "APPLICATION_REVIEW_COMPLETED")
                .map(AuditEvent::getId).orElse(null);
        List<String> automatedFindings = saved.stream().map(check -> check.getAgentType() + ": " + check.getCheckStatus()).toList();
        var decisionAudit = auditEvents.findTopByApplication_IdAndEventTypeOrderByCreatedAtDesc(application.getId(), "HUMAN_DECISION_RECORDED");
        Map<String, Object> details = decisionAudit.map(AuditEvent::getEventDetails).orElse(Map.of());
        String humanDecision = (String) details.getOrDefault("decision", null);
        String humanNote = (String) details.getOrDefault("note", null);
        String humanReviewer = decisionAudit.map(AuditEvent::getActorReference).map(reference -> users.findById(UUID.fromString(reference)))
                .flatMap(value -> value).map(CustomerUser::getFullName).orElse(null);
        List<String> requestedDocuments = details.get("requestedDocuments") instanceof List<?> values
                ? values.stream().map(Object::toString).toList() : List.of();
        List<String> nextSteps = humanDecision == null && saved.isEmpty() ? List.of("Run automated pre-review checks")
                : humanDecision == null && !missing.isEmpty() ? List.of("Customer must upload: " + String.join(", ", missing), "Reviewer must reassess the submitted documents")
                : humanDecision == null ? List.of("Reviewer must record APPROVE, DECLINE, or REQUEST_DOCUMENTS")
                : "REQUEST_DOCUMENTS".equals(humanDecision) ? List.of("Customer must upload: " + String.join(", ", requestedDocuments), "Reviewer must reassess the new documents")
                : List.of("Human decision recorded: " + humanDecision);
        return new ApplicationReviewResponse(application.getId(), application.getApplicationReference(), recommendation, missing,
                nextSteps,
                humanDecision == null, auditId, saved.stream().map(AgentCheckResponse::from).toList(),
                automatedFindings, humanDecision, humanReviewer, humanNote,
                decisionAudit.map(AuditEvent::getCreatedAt).orElse(null), requestedDocuments);
    }

    @Transactional(readOnly = true)
    public LoanApplicationResponse get(UUID id) {
        return applications.findById(id).map(LoanApplicationResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Loan application"));
    }

    @Transactional(readOnly = true)
    public PageResponse<ApplicationDocumentResponse> listDocuments(UUID applicationId, PageRequest page) {
        requireApplication(applicationId);
        return PageResponse.from(documents.findByApplication_IdOrderByUploadedAtDesc(applicationId, page),
                ApplicationDocumentResponse::from);
    }

    @Transactional(readOnly = true)
    public PageResponse<AgentCheckResponse> listChecks(UUID applicationId, PageRequest page) {
        requireApplication(applicationId);
        return PageResponse.from(checks.findByApplication_IdOrderByCreatedAtDesc(applicationId, page),
                AgentCheckResponse::from);
    }

    private void requireApplication(UUID id) {
        if (!applications.existsById(id)) {
            throw new ResourceNotFoundException("Loan application");
        }
    }
}
