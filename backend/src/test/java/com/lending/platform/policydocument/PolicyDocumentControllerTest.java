package com.lending.platform.policydocument;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import com.lending.platform.common.api.ResourceNotFoundException;

@WebMvcTest(PolicyDocumentController.class)
@AutoConfigureMockMvc(addFilters = false)
class PolicyDocumentControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PolicyDocumentService service;

    @Test
    void multipartUploadBindsMetadataAndReturnsTypedStatus() throws Exception {
        UUID id = UUID.randomUUID();
        PolicyDocument response = document(id, PolicyDocumentStatus.PROCESSED);
        when(service.upload(any(PolicyDocumentRequest.class), any())).thenReturn(response);
        MockMultipartFile file = new MockMultipartFile("file", "policy.pdf", "application/pdf", new byte[]{1, 2});

        mockMvc.perform(multipart("/api/v1/policies/documents")
                        .file(file)
                        .param("documentName", "Vehicle Loan Policy")
                        .param("documentType", "LENDING_POLICY")
                        .param("version", "2026.1")
                        .param("effectiveDate", "2026-07-01"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentId").value(id.toString()))
                .andExpect(jsonPath("$.status").value("PROCESSED"))
                .andExpect(jsonPath("$.fileSizeBytes").value(2))
                .andExpect(jsonPath("$.storageKey").doesNotExist());
    }

    @Test
    void multipartUploadValidatesRequiredMetadataBeforeCallingService() throws Exception {
        mockMvc.perform(multipart("/api/v1/policies/documents")
                        .file(new MockMultipartFile("file", "policy.pdf", "application/pdf", new byte[]{1})))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void metadataGetAndPaginatedListReturnStandardContracts() throws Exception {
        UUID id = UUID.randomUUID();
        PolicyDocument policy = document(id, PolicyDocumentStatus.PROCESSED);
        when(service.get(id)).thenReturn(policy);
        when(service.list(any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(policy), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/policies/documents/{documentId}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value(id.toString()))
                .andExpect(jsonPath("$.status").value("PROCESSED"));
        mockMvc.perform(get("/api/v1/policies/documents").param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].documentName").value("Vehicle Loan Policy"));
    }

    @Test
    void nonexistentMetadataUsesShared404Envelope() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.get(id)).thenThrow(new ResourceNotFoundException("Policy document"));

        mockMvc.perform(get("/api/v1/policies/documents/{documentId}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    private PolicyDocument document(UUID id, PolicyDocumentStatus status) {
        PolicyDocument document = new PolicyDocument("Vehicle Loan Policy", "LENDING_POLICY", "2026.1", null,
                status, "filesystem", id + ".pdf", 2, "a".repeat(64));
        ReflectionTestUtils.setField(document, "id", id);
        return document;
    }
}
