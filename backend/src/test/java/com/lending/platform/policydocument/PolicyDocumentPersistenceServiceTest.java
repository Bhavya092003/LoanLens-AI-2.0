package com.lending.platform.policydocument;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PolicyDocumentPersistenceServiceTest {
    private final PolicyDocumentRepository documents = org.mockito.Mockito.mock(PolicyDocumentRepository.class);
    private final PolicyDocumentPageRepository pages = org.mockito.Mockito.mock(PolicyDocumentPageRepository.class);
    private final PolicyDocumentPersistenceService service = new PolicyDocumentPersistenceService(documents, pages);

    @Test
    void completionPersistsEveryPageAndMarksTextlessPdfForOcr() {
        UUID id = UUID.randomUUID();
        PolicyDocument document = document(id, PolicyDocumentStatus.PROCESSING);
        when(documents.findByIdForUpdate(id)).thenReturn(Optional.of(document));
        when(pages.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        List<PolicyPdfExtractionService.ExtractedPage> extracted = List.of(
                new PolicyPdfExtractionService.ExtractedPage(1, ""),
                new PolicyPdfExtractionService.ExtractedPage(2, " "));

        PolicyDocument completed = service.completeExtraction(id, extracted, false);

        assertThat(completed.getStatus()).isEqualTo(PolicyDocumentStatus.NEEDS_OCR);
        assertThat(completed.getExtractionCompletedAt()).isNotNull();
        verify(pages).deleteAllByPolicyDocument_Id(id);
        verify(pages).saveAll(org.mockito.ArgumentMatchers.argThat(saved -> {
            java.util.Iterator<PolicyDocumentPage> iterator = saved.iterator();
            if (!iterator.hasNext()) return false;
            PolicyDocumentPage first = iterator.next();
            if (!iterator.hasNext()) return false;
            PolicyDocumentPage second = iterator.next();
            return !iterator.hasNext() && first.getPageNumber() == 1 && first.getExtractedText().isEmpty()
                    && second.getPageNumber() == 2;
        }));
    }

    @Test
    void failedDocumentCanBeClaimedForRetryAndOldPageRowsAreReplacedOnCompletion() {
        UUID id = UUID.randomUUID();
        PolicyDocument document = document(id, PolicyDocumentStatus.FAILED);
        when(documents.findByIdForUpdate(id)).thenReturn(Optional.of(document));
        when(pages.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        PolicyDocument claimed = service.claimForProcessing(id, true);
        assertThat(claimed.getStatus()).isEqualTo(PolicyDocumentStatus.PROCESSING);
        service.completeExtraction(id, List.of(new PolicyPdfExtractionService.ExtractedPage(1, "recovered")), true);

        assertThat(document.getStatus()).isEqualTo(PolicyDocumentStatus.PROCESSED);
        verify(pages).deleteAllByPolicyDocument_Id(id);
    }

    private PolicyDocument document(UUID id, PolicyDocumentStatus status) {
        PolicyDocument document = new PolicyDocument("Policy", "LENDING_POLICY", "v1", null,
                status, "filesystem", "storage.pdf", 10L, "a".repeat(64));
        ReflectionTestUtils.setField(document, "id", id);
        return document;
    }
}
