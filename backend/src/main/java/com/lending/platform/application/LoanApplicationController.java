package com.lending.platform.application;

import com.lending.platform.common.api.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

@Validated
@RestController
@RequestMapping("/api/v1/applications")
public class LoanApplicationController {
    private final LoanApplicationService service;

    public LoanApplicationController(LoanApplicationService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LoanApplicationResponse create(@Valid @RequestBody CreateApplicationRequest request) {
        return service.create(request);
    }

    @GetMapping("/review-queue")
    public List<LoanApplicationResponse> reviewQueue() {
        return service.reviewQueue();
    }

    @GetMapping("/{applicationId}")
    public ResponseEntity<LoanApplicationResponse> get(@PathVariable UUID applicationId) {
        return ResponseEntity.ok(service.get(applicationId));
    }

    @PostMapping("/{applicationId}/documents")
    public ApplicationDocumentResponse registerDocument(@PathVariable UUID applicationId,
                                 @RequestParam @NotBlank @Size(max = 64) String documentType,
                                 @RequestParam MultipartFile file) {
        return service.uploadDocument(applicationId, documentType, file);
    }

    @GetMapping("/{applicationId}/documents")
    public PageResponse<ApplicationDocumentResponse> documents(
            @PathVariable UUID applicationId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.listDocuments(applicationId, PageRequest.of(page, size));
    }

    @PostMapping("/{applicationId}/submit")
    public LoanApplicationResponse submit(@PathVariable UUID applicationId) {
        return service.submit(applicationId);
    }

    @PostMapping("/{applicationId}/review")
    public ApplicationReviewResponse requestReview(@PathVariable UUID applicationId, @Valid @RequestBody ReviewRequest request) {
        return service.review(applicationId, request);
    }

    @PostMapping("/{applicationId}/decision")
    public LoanApplicationResponse decision(@PathVariable UUID applicationId,
                                             @Valid @RequestBody HumanDecisionRequest request,
                                             Authentication authentication) {
        return service.decide(applicationId, request, (UUID) authentication.getDetails());
    }

    @GetMapping("/{applicationId}/review")
    public ApplicationReviewResponse reviews(@PathVariable UUID applicationId) {
        return service.getReview(applicationId);
    }
}
