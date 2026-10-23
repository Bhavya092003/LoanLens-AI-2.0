package com.lending.platform.policyqa;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lending.platform.policydocument.PolicyChunkRepository;
import com.lending.platform.policydocument.PolicyDocument;
import com.lending.platform.policydocument.PolicyDocumentPage;
import com.lending.platform.policydocument.PolicyDocumentPageRepository;
import com.lending.platform.policydocument.PolicyDocumentRepository;
import com.lending.platform.policydocument.PolicyDocumentStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class PolicyVectorServiceTest {
    private final PolicyDocumentRepository documents = mock(PolicyDocumentRepository.class);
    private final PolicyDocumentPageRepository pages = mock(PolicyDocumentPageRepository.class);
    private final PolicyChunkRepository chunks = mock(PolicyChunkRepository.class);
    private final EmbeddingProvider embeddings = mock(EmbeddingProvider.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PolicyVectorService service = new PolicyVectorService(documents, pages, chunks,
            new PolicyChunkingService(100, 10), embeddings, jdbc, 1536, 5, 0.25);

    @Test
    void rejectsMismatchedDimensionBeforeReplacingExistingChunks() {
        UUID id = UUID.randomUUID();
        PolicyDocument document = new PolicyDocument("Policy", "TYPE", "v1", null, PolicyDocumentStatus.PROCESSED, null, null);
        ReflectionTestUtils.setField(document, "id", id);
        when(documents.findById(id)).thenReturn(Optional.of(document));
        when(pages.findByPolicyDocument_IdOrderByPageNumber(id)).thenReturn(List.of(new PolicyDocumentPage(document, 1, "policy text")));
        when(embeddings.embedAll(List.of("policy text"))).thenReturn(List.of(List.of(0.1, 0.2)));
        assertThatThrownBy(() -> service.index(id)).isInstanceOf(AiProviderException.class).hasMessageContaining("invalid dimension");
        verify(chunks, never()).deleteAllByPolicyDocument_Id(id);
    }

    @Test
    void preservesExistingChunksWhenEmbeddingProviderFails() {
        UUID id = UUID.randomUUID();
        PolicyDocument document = new PolicyDocument("Policy", "TYPE", "v1", null, PolicyDocumentStatus.PROCESSED, null, null);
        ReflectionTestUtils.setField(document, "id", id);
        when(documents.findById(id)).thenReturn(Optional.of(document));
        when(pages.findByPolicyDocument_IdOrderByPageNumber(id)).thenReturn(List.of(new PolicyDocumentPage(document, 1, "policy text")));
        when(embeddings.embedAll(List.of("policy text"))).thenThrow(new AiProviderException("unavailable"));
        assertThatThrownBy(() -> service.index(id)).isInstanceOf(AiProviderException.class);
        verify(chunks, never()).deleteAllByPolicyDocument_Id(id);
    }
}
