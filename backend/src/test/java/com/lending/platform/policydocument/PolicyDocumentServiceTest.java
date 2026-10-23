package com.lending.platform.policydocument;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lending.platform.common.api.ApiException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.unit.DataSize;
import com.lending.platform.policyqa.PolicyVectorService;

class PolicyDocumentServiceTest {
    private final PolicyDocumentPersistenceService persistence = org.mockito.Mockito.mock(PolicyDocumentPersistenceService.class);
    private final PolicyFileStorage storage = org.mockito.Mockito.mock(PolicyFileStorage.class);
    private final PolicyPdfExtractionService extractor = org.mockito.Mockito.mock(PolicyPdfExtractionService.class);
    private final PolicyVectorService vectors = org.mockito.Mockito.mock(PolicyVectorService.class);
    private final OcrSpaceProvider ocr = org.mockito.Mockito.mock(OcrSpaceProvider.class);

    @Test
    void uploadIgnoresUnsafeOriginalFilenameAndReturnsOnlyAfterPagePersistence() {
        PolicyDocumentService service = new PolicyDocumentService(persistence, storage, extractor, vectors, ocr, DataSize.ofMegabytes(1));
        byte[] bytes = {1, 2, 3};
        MockMultipartFile file = new MockMultipartFile("file", "../../outside.pdf", "text/plain", bytes);
        PolicyDocument document = document(PolicyDocumentStatus.UPLOADED);
        when(storage.store(bytes)).thenReturn(new PolicyFileStorage.StoredPolicyFile("filesystem", "safe.pdf", 3, "a".repeat(64)));
        when(persistence.createUploaded(any(), any())).thenReturn(document);
        when(persistence.claimForProcessing(document.getId(), false)).thenReturn(document);
        when(storage.read("safe.pdf", 1024 * 1024)).thenReturn(bytes);
        when(extractor.extract(bytes)).thenReturn(new PolicyPdfExtractionService.ExtractionResult(
                List.of(new PolicyPdfExtractionService.ExtractedPage(1, "page text")), true));
        when(persistence.completeExtraction(eq(document.getId()), any(), anyBoolean())).thenAnswer(invocation -> {
            document.completeExtraction(PolicyDocumentStatus.PROCESSED);
            return document;
        });
        when(persistence.get(document.getId())).thenReturn(document);

        PolicyDocument result = service.upload(request(), file);

        assertThat(result.getStatus()).isEqualTo(PolicyDocumentStatus.PROCESSED);
        verify(extractor).validate(bytes);
        verify(persistence).completeExtraction(eq(document.getId()), any(), eq(true));
        verify(vectors).index(document.getId());
    }

    @Test
    void rejectsEmptyAndOversizedFilesBeforeStorage() {
        PolicyDocumentService smallLimit = new PolicyDocumentService(persistence, storage, extractor, vectors, ocr, DataSize.ofBytes(2));

        assertThatThrownBy(() -> smallLimit.upload(request(), new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0])))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("non-empty PDF");
        assertThatThrownBy(() -> smallLimit.upload(request(), new MockMultipartFile("file", "large.pdf", "application/pdf", new byte[3])))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("configured upload size limit");
        verify(storage, never()).store(any());
    }

    @Test
    void extractionFailureIsRecordedWithoutPersistingSuccessOrLeakingExceptionText() {
        PolicyDocumentService service = new PolicyDocumentService(persistence, storage, extractor, vectors, ocr, DataSize.ofMegabytes(1));
        byte[] bytes = {1, 2, 3};
        PolicyDocument document = document(PolicyDocumentStatus.UPLOADED);
        when(storage.store(bytes)).thenReturn(new PolicyFileStorage.StoredPolicyFile("filesystem", "safe.pdf", 3, "a".repeat(64)));
        when(persistence.createUploaded(any(), any())).thenReturn(document);
        when(persistence.claimForProcessing(document.getId(), false)).thenReturn(document);
        when(storage.read("safe.pdf", 1024 * 1024)).thenReturn(bytes);
        when(extractor.extract(bytes)).thenThrow(new IllegalStateException("sensitive PDF contents"));
        when(persistence.markFailed(eq(document.getId()), any())).thenAnswer(invocation -> {
            document.markFailed(invocation.getArgument(1));
            return document;
        });

        PolicyDocument result = service.upload(request(), new MockMultipartFile("file", "policy.pdf", "application/pdf", bytes));

        assertThat(result.getStatus()).isEqualTo(PolicyDocumentStatus.FAILED);
        assertThat(result.getProcessingError()).doesNotContain("sensitive PDF contents");
        verify(persistence, never()).completeExtraction(any(), any(), anyBoolean());
    }

    private PolicyDocumentRequest request() {
        return new PolicyDocumentRequest("Vehicle Loan Policy", "LENDING_POLICY", "2026.1", null);
    }

    private PolicyDocument document(PolicyDocumentStatus status) {
        PolicyDocument document = new PolicyDocument("Vehicle Loan Policy", "LENDING_POLICY", "2026.1", null,
                status, "filesystem", "safe.pdf", 3, "a".repeat(64));
        ReflectionTestUtils.setField(document, "id", UUID.randomUUID());
        return document;
    }
}
