package com.lending.platform.policyqa;

import static org.assertj.core.api.Assertions.assertThat;

import com.lending.platform.policydocument.PolicyDocument;
import com.lending.platform.policydocument.PolicyDocumentPage;
import com.lending.platform.policydocument.PolicyDocumentStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

class PolicyChunkingServiceTest {
    @Test
    void createsNonEmptyOverlappingChunksWithPageReferences() {
        PolicyDocument doc = new PolicyDocument("Policy", "TYPE", "v1", null, PolicyDocumentStatus.PROCESSED, null, null);
        String text = "A".repeat(95) + ". " + "B".repeat(95);
        PolicyChunkingService service = new PolicyChunkingService(100, 20);
        var chunks = service.chunk(List.of(new PolicyDocumentPage(doc, 2, text), new PolicyDocumentPage(doc, 3, "  ")));
        assertThat(chunks).hasSizeGreaterThan(1).allSatisfy(chunk -> {
            assertThat(chunk.text()).isNotBlank();
            assertThat(chunk.page()).isEqualTo(2);
        });
        assertThat(chunks.get(0).index()).isZero();
        assertThat(chunks.get(1).index()).isEqualTo(1);
    }

    @Test
    void emptyPageTextProducesNoChunks() {
        PolicyDocument doc = new PolicyDocument("Policy", "TYPE", "v1", null, PolicyDocumentStatus.PROCESSED, null, null);
        assertThat(new PolicyChunkingService(100, 10).chunk(List.of(new PolicyDocumentPage(doc, 1, " \n ")))).isEmpty();
    }
}
