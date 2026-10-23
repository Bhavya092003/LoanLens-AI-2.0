package com.lending.platform.policydocument;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.lending.platform.common.api.PageResponse;

@RestController
@RequestMapping("/api/v1/policies/documents")
public class PolicyDocumentController {
    private final PolicyDocumentService service;

    public PolicyDocumentController(PolicyDocumentService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PolicyDocumentResponse register(@Valid @RequestBody PolicyDocumentRequest request) {
        return PolicyDocumentResponse.from(service.register(request));
    }

    @PostMapping(consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public PolicyDocumentResponse upload(@Valid @ModelAttribute PolicyDocumentRequest request,
                                         @RequestParam("file") MultipartFile file) {
        return PolicyDocumentResponse.from(service.upload(request, file));
    }

    @GetMapping("/{documentId}")
    public ResponseEntity<PolicyDocumentResponse> get(@PathVariable UUID documentId) {
        return ResponseEntity.ok(PolicyDocumentResponse.from(service.get(documentId)));
    }

    @GetMapping
    public PageResponse<PolicyDocumentResponse> list(
            @RequestParam(required = false) String documentType,
            @RequestParam(required = false) String version,
            @RequestParam(required = false) PolicyDocumentStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(service.list(documentType, version, status,
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")))),
                PolicyDocumentResponse::from);
    }

    @GetMapping("/{documentId}/pages")
    public PageResponse<PolicyDocumentPageResponse> pages(
            @PathVariable UUID documentId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(service.pages(documentId,
                PageRequest.of(page, size, Sort.by(Sort.Order.asc("pageNumber")))), PolicyDocumentPageResponse::from);
    }

    @PostMapping("/{documentId}/retry")
    public ResponseEntity<PolicyDocumentResponse> retry(@PathVariable UUID documentId) {
        return ResponseEntity.ok(PolicyDocumentResponse.from(service.retry(documentId)));
    }
}
