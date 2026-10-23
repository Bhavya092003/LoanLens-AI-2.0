package com.lending.platform.policydocument;

import com.lending.platform.common.api.ApiException;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import com.lending.platform.policyqa.PolicyVectorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

@Service
public class PolicyDocumentService {
    private static final Logger log = LoggerFactory.getLogger(PolicyDocumentService.class);
    private static final String SAFE_FAILURE_REASON =
            "PDF extraction or semantic indexing failed; check storage, provider, and vector configuration, then retry the document";

    private final PolicyDocumentPersistenceService persistence;
    private final PolicyFileStorage storage;
    private final PolicyPdfExtractionService extractor;
    private final long maxFileSizeBytes;
    private final PolicyVectorService vectorService;
    private final OcrSpaceProvider ocrProvider;

    public PolicyDocumentService(PolicyDocumentPersistenceService persistence, PolicyFileStorage storage,
                                 PolicyPdfExtractionService extractor,
                                 PolicyVectorService vectorService,
                                 OcrSpaceProvider ocrProvider,
                                 @Value("${app.policy-storage.max-file-size:20MB}") DataSize maxFileSize) {
        this.persistence = persistence;
        this.storage = storage;
        this.extractor = extractor;
        this.vectorService = vectorService;
        this.ocrProvider = ocrProvider;
        this.maxFileSizeBytes = maxFileSize.toBytes();
    }

    public PolicyDocument register(PolicyDocumentRequest request) {
        return persistence.register(request);
    }

    public PolicyDocument upload(PolicyDocumentRequest request, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_FILE", "A non-empty PDF file is required");
        }
        if (file.getSize() > maxFileSizeBytes) {
            throw tooLarge();
        }
        persistence.assertVersionAvailable(request);

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FILE", "The uploaded file could not be read");
        }
        if (content.length == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_FILE", "A non-empty PDF file is required");
        }
        if (content.length > maxFileSizeBytes) {
            throw tooLarge();
        }
        extractor.validate(content);

        // Storage is written first. If Neon rejects the following transaction, the uniquely keyed file is retained
        // for safe reconciliation instead of being deleted while a database commit may have succeeded.
        PolicyFileStorage.StoredPolicyFile storedFile = storage.store(content);
        PolicyDocument document = persistence.createUploaded(request, storedFile);
        return process(document.getId(), false);
    }

    public PolicyDocument retry(UUID id) {
        return process(id, true);
    }

    public PolicyDocument get(UUID id) {
        return persistence.get(id);
    }

    public Page<PolicyDocument> list(String documentType, String version, PolicyDocumentStatus status, Pageable pageable) {
        return persistence.list(documentType, version, status, pageable);
    }

    public Page<PolicyDocumentPage> pages(UUID documentId, Pageable pageable) {
        return persistence.pages(documentId, pageable);
    }

    private PolicyDocument process(UUID id, boolean retry) {
        PolicyDocument claimed = persistence.claimForProcessing(id, retry);
        try {
            byte[] content = storage.read(claimed.getStorageKey(), maxFileSizeBytes);
            PolicyPdfExtractionService.ExtractionResult result = extractor.extract(content);
            if (!result.hasText() && ocrProvider.isConfigured()) {
                List<PolicyPdfExtractionService.ExtractedPage> ocrPages =
                        ocrProvider.extract(content, result.pages().size());
                result = new PolicyPdfExtractionService.ExtractionResult(ocrPages,
                        ocrPages.stream().anyMatch(page -> !page.text().isBlank()));
            }
            PolicyDocument extracted = persistence.completeExtraction(id, result.pages(), result.hasText());
            if (result.hasText()) {
                vectorService.index(id);
                return persistence.get(id);
            }
            return extracted;
        } catch (RuntimeException exception) {
            log.warn("Policy document processing failed for id={} ({})", id, exception.getClass().getSimpleName());
            return persistence.markFailed(id, SAFE_FAILURE_REASON);
        }
    }

    private ApiException tooLarge() {
        return new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE",
                "The PDF exceeds the configured upload size limit");
    }
}
