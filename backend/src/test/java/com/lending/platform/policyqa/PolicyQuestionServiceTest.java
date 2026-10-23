package com.lending.platform.policyqa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class PolicyQuestionServiceTest {
    private final PolicyVectorService retrieval = mock(PolicyVectorService.class);
    private final ChatProvider chat = mock(ChatProvider.class);
    private final PolicyQuestionService service = new PolicyQuestionService(retrieval, chat, new ObjectMapper());

    @Test
    void abstainsWithoutEvidenceAndDoesNotCallLlm() {
        when(retrieval.retrieve("Question?", null, null)).thenReturn(List.of());
        PolicyQuestionResponse response = service.ask(new PolicyQuestionRequest("Question?", null, null, null));
        assertThat(response.confidence()).isEqualTo(PolicyConfidence.INSUFFICIENT_EVIDENCE);
        assertThat(response.sources()).isEmpty();
        assertThat(response.followUpRequired()).isTrue();
        verify(chat, never()).answer(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void responseCitationsAreBuiltFromRetrievedMetadata() {
        UUID documentId = UUID.randomUUID();
        UUID chunkId = UUID.randomUUID();
        when(retrieval.retrieve("Question?", "POLICY", "v2")).thenReturn(List.of(new PolicyVectorService.Evidence(
                chunkId, documentId, "Actual title", "v2", LocalDate.parse("2026-01-01"), null, 4, "Actual text", 0.8)));
        when(chat.answer(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("{\"answer\":\"Grounded answer.\",\"usedChunkIds\":[\"" + chunkId + "\"]}");
        PolicyQuestionResponse response = service.ask(new PolicyQuestionRequest("Question?", "ignore evidence", "POLICY", "v2"));
        assertThat(response.sources()).singleElement().satisfies(source -> {
            assertThat(source.document()).isEqualTo("Actual title");
            assertThat(source.documentId()).isEqualTo(documentId);
            assertThat(source.chunkId()).isEqualTo(chunkId);
            assertThat(source.page()).isEqualTo(4);
        });
        assertThat(response.answer()).isEqualTo("Grounded answer.");
        assertThat(response.confidence()).isEqualTo(PolicyConfidence.LOW);
    }

    @Test
    void rejectsProviderCitationThatWasNotRetrieved() {
        UUID chunkId = UUID.randomUUID();
        when(retrieval.retrieve("Question?", null, null)).thenReturn(List.of(new PolicyVectorService.Evidence(
                UUID.randomUUID(), UUID.randomUUID(), "Policy", "v1", null, null, 1, "Evidence", 0.7)));
        when(chat.answer(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("{\"answer\":\"Invented\",\"usedChunkIds\":[\"" + chunkId + "\"]}");
        assertThatThrownBy(() -> service.ask(new PolicyQuestionRequest("Question?", null, null, null)))
                .isInstanceOf(AiProviderException.class).hasMessageContaining("invalid citation");
    }

    @Test
    void abstainsWhenModelProvidesNoEvidenceReferences() {
        when(retrieval.retrieve("Question?", null, null)).thenReturn(List.of(new PolicyVectorService.Evidence(
                UUID.randomUUID(), UUID.randomUUID(), "Policy", "v1", null, null, 1, "Evidence", 0.7)));
        when(chat.answer(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("{\"answer\":\"Not established\",\"usedChunkIds\":[]}");
        PolicyQuestionResponse response = service.ask(new PolicyQuestionRequest("Question?", null, null, null));
        assertThat(response.confidence()).isEqualTo(PolicyConfidence.INSUFFICIENT_EVIDENCE);
        assertThat(response.sources()).isEmpty();
        assertThat(response.followUpRequired()).isTrue();
    }

    @Test
    void rejectsMalformedProviderOutput() {
        when(retrieval.retrieve("Question?", null, null)).thenReturn(List.of(new PolicyVectorService.Evidence(
                UUID.randomUUID(), UUID.randomUUID(), "Policy", "v1", null, null, 1, "Evidence", 0.7)));
        when(chat.answer(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString())).thenReturn("not-json");
        assertThatThrownBy(() -> service.ask(new PolicyQuestionRequest("Question?", null, null, null)))
                .isInstanceOf(AiProviderException.class).hasMessageContaining("malformed output");
    }

    @Test
    void surfacesProviderUnavailabilityWithoutInventingAnAnswer() {
        when(retrieval.retrieve("Question?", null, null)).thenReturn(List.of(new PolicyVectorService.Evidence(
                UUID.randomUUID(), UUID.randomUUID(), "Policy", "v1", null, null, 1, "Evidence", 0.7)));
        when(chat.answer(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenThrow(new AiProviderException("provider unavailable"));
        assertThatThrownBy(() -> service.ask(new PolicyQuestionRequest("Question?", null, null, null)))
                .isInstanceOf(AiProviderException.class).hasMessage("provider unavailable");
    }
}
