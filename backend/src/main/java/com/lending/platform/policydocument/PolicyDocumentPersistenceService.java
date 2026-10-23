package com.lending.platform.policydocument;

import com.lending.platform.common.api.ApiException;
import com.lending.platform.common.api.ResourceNotFoundException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PolicyDocumentPersistenceService {
    private final PolicyDocumentRepository documentRepository;
    private final PolicyDocumentPageRepository pageRepository;

    public PolicyDocumentPersistenceService(PolicyDocumentRepository documentRepository,
                                            PolicyDocumentPageRepository pageRepository) {
        this.documentRepository = documentRepository;
        this.pageRepository = pageRepository;
    }

    @Transactional
    public PolicyDocument register(PolicyDocumentRequest request) {
        ensureVersionAvailable(request);
        return documentRepository.saveAndFlush(new PolicyDocument(request.documentName(), request.documentType(),
                request.version(), request.effectiveDate(), PolicyDocumentStatus.REGISTERED, null, null));
    }

    @Transactional
    public PolicyDocument createUploaded(PolicyDocumentRequest request, PolicyFileStorage.StoredPolicyFile storedFile) {
        ensureVersionAvailable(request);
        PolicyDocument document = new PolicyDocument(request.documentName(), request.documentType(), request.version(),
                request.effectiveDate(), PolicyDocumentStatus.UPLOADED, storedFile.provider(), storedFile.storageKey(),
                storedFile.sizeBytes(), storedFile.sha256());
        return documentRepository.saveAndFlush(document);
    }

    @Transactional(readOnly = true)
    public PolicyDocument get(UUID id) {
        return documentRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Policy document"));
    }

    @Transactional(readOnly = true)
    public Page<PolicyDocument> list(String documentType, String version, PolicyDocumentStatus status, Pageable pageable) {
        return documentType == null || documentType.isBlank()
                ? documentRepository.findFilteredWithoutType(version, status, pageable)
                : documentRepository.findFiltered(documentType, version, status, pageable);
    }

    @Transactional(readOnly = true)
    public void assertVersionAvailable(PolicyDocumentRequest request) {
        ensureVersionAvailable(request);
    }

    @Transactional(readOnly = true)
    public Page<PolicyDocumentPage> pages(UUID documentId, Pageable pageable) {
        if (!documentRepository.existsById(documentId)) {
            throw new ResourceNotFoundException("Policy document");
        }
        return pageRepository.findByPolicyDocument_Id(documentId, pageable);
    }

    @Transactional
    public PolicyDocument claimForProcessing(UUID id, boolean retry) {
        PolicyDocument document = documentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Policy document"));
        if (document.getStatus() == PolicyDocumentStatus.PROCESSING) {
            throw new ApiException(HttpStatus.CONFLICT, "DOCUMENT_PROCESSING", "The policy document is already being processed");
        }
        boolean eligible = document.getStatus() == PolicyDocumentStatus.UPLOADED
                || (retry && document.getStatus() == PolicyDocumentStatus.FAILED);
        if (!eligible) {
            throw new ApiException(HttpStatus.CONFLICT, "DOCUMENT_NOT_RETRYABLE",
                    "Only uploaded or failed policy documents can be processed");
        }
        document.markProcessing();
        return document;
    }

    @Transactional
    public PolicyDocument completeExtraction(UUID id, List<PolicyPdfExtractionService.ExtractedPage> extractedPages,
                                             boolean hasText) {
        PolicyDocument document = documentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Policy document"));
        if (document.getStatus() != PolicyDocumentStatus.PROCESSING) {
            throw new ApiException(HttpStatus.CONFLICT, "DOCUMENT_NOT_PROCESSING", "The policy document is not being processed");
        }
        pageRepository.deleteAllByPolicyDocument_Id(id);
        List<PolicyDocumentPage> pages = extractedPages.stream()
                .map(page -> new PolicyDocumentPage(document, page.pageNumber(), page.text()))
                .toList();
        pageRepository.saveAll(pages);
        document.completeExtraction(hasText ? PolicyDocumentStatus.PROCESSED : PolicyDocumentStatus.NEEDS_OCR);
        return document;
    }

    @Transactional
    public PolicyDocument markFailed(UUID id, String safeReason) {
        PolicyDocument document = documentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Policy document"));
        document.markFailed(safeReason);
        return document;
    }

    private void ensureVersionAvailable(PolicyDocumentRequest request) {
        if (documentRepository.existsByDocumentNameIgnoreCaseAndVersion(request.documentName(), request.version())) {
            throw new ApiException(HttpStatus.CONFLICT, "POLICY_VERSION_EXISTS",
                    "A policy document with this name and version is already registered");
        }
    }
}
