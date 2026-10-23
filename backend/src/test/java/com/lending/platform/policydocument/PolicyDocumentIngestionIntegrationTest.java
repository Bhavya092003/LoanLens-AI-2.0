package com.lending.platform.policydocument;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lending.platform.DigitalLendingApplication;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.lending.platform.policyqa.EmbeddingProvider;
import java.util.List;

@SpringBootTest(classes = DigitalLendingApplication.class)
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class PolicyDocumentIngestionIntegrationTest {
    private static final Path STORAGE_DIRECTORY = createStorageDirectory();

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg17");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PolicyDocumentRepository documentRepository;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("app.policy-storage.directory", STORAGE_DIRECTORY::toString);
    }

    @Test
    void uploadPersistsExtractedPagesAndKeepsPriorVersionsWhenRetrying() throws Exception {
        byte[] pdf = pdfWithTextAndBlankPage();
        String response = mockMvc.perform(multipart("/api/v1/policies/documents")
                        .file(new MockMultipartFile("file", "../../unsafe-name.pdf", "application/pdf", pdf))
                        .param("documentName", "Integration Test Policy")
                        .param("documentType", "LENDING_POLICY")
                        .param("version", "v1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("INDEXED"))
                .andExpect(jsonPath("$.fileSizeBytes").value(pdf.length))
                .andExpect(jsonPath("$.contentSha256").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        UUID documentId = UUID.fromString(com.fasterxml.jackson.databind.json.JsonMapper.builder()
                .build().readTree(response).get("documentId").asText());

        mockMvc.perform(get("/api/v1/policies/documents/{documentId}/pages", documentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].pageNumber").value(1))
                .andExpect(jsonPath("$.content[0].text").value("First policy page"))
                .andExpect(jsonPath("$.content[1].pageNumber").value(2))
                .andExpect(jsonPath("$.content[1].text").value(""));
        mockMvc.perform(get("/api/v1/policies/documents").param("documentType", "lending_policy")
                        .param("page", "0").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(multipart("/api/v1/policies/documents")
                        .file(new MockMultipartFile("file", "again.pdf", "application/pdf", pdf))
                        .param("documentName", "Integration Test Policy")
                        .param("documentType", "LENDING_POLICY")
                        .param("version", "v1"))
                .andExpect(status().isConflict());

        PolicyDocument failedForRetry = documentRepository.findById(documentId).orElseThrow();
        failedForRetry.markFailed("test-only retry state");
        documentRepository.saveAndFlush(failedForRetry);
        mockMvc.perform(post("/api/v1/policies/documents/{documentId}/retry", documentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INDEXED"));
        mockMvc.perform(get("/api/v1/policies/documents/{documentId}/pages", documentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(multipart("/api/v1/policies/documents")
                        .file(new MockMultipartFile("file", "new-version.pdf", "application/pdf", pdf))
                        .param("documentName", "Integration Test Policy")
                        .param("documentType", "LENDING_POLICY")
                        .param("version", "v2"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value("v2"));
        mockMvc.perform(get("/api/v1/policies/documents/{documentId}", documentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("v1"));
    }

    private static byte[] pdfWithTextAndBlankPage() throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage first = new PDPage();
            document.addPage(first);
            try (PDPageContentStream content = new PDPageContentStream(document, first)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(50, 700);
                content.showText("First policy page");
                content.endText();
            }
            document.addPage(new PDPage());
            document.save(output);
            return output.toByteArray();
        }
    }

    private static Path createStorageDirectory() {
        try {
            return Files.createTempDirectory("policy-ingestion-test-");
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    @TestConfiguration
    static class EmbeddingTestConfiguration {
        @Bean @Primary
        EmbeddingProvider testEmbeddingProvider() {
            return text -> {
                java.util.ArrayList<Double> values = new java.util.ArrayList<>(java.util.Collections.nCopies(1536, 0.0));
                values.set(0, 1.0);
                return List.copyOf(values);
            };
        }
    }
}
