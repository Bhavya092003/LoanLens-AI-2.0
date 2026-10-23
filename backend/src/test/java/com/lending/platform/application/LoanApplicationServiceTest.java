package com.lending.platform.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lending.platform.audit.AuditEventRepository;
import com.lending.platform.discovery.EmiCalculationService;
import com.lending.platform.loanproduct.LoanProductRepository;
import com.lending.platform.user.CustomerUser;
import com.lending.platform.user.CustomerUserRepository;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.unit.DataSize;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import java.util.List;

class LoanApplicationServiceTest {
    @TempDir java.nio.file.Path directory;

    @Test
    void duplicateDocumentUploadReturnsExistingRecordWithoutWritingAnotherFile() throws Exception {
        LoanApplicationRepository applications = mock(LoanApplicationRepository.class);
        CustomerUserRepository users = mock(CustomerUserRepository.class);
        LoanProductRepository products = mock(LoanProductRepository.class);
        ApplicationStatusHistoryRepository history = mock(ApplicationStatusHistoryRepository.class);
        ApplicationDocumentRepository documents = mock(ApplicationDocumentRepository.class);
        AgentCheckRepository checks = mock(AgentCheckRepository.class);
        AuditEventRepository audits = mock(AuditEventRepository.class);
        LoanApplication app = new LoanApplication("LA-EXAMPLE", new CustomerUser(null, "Demo", null, null), null,
                BigDecimal.valueOf(50000), 24, BigDecimal.valueOf(30000), ApplicationStatus.DRAFT);
        UUID appId = UUID.randomUUID();
        ReflectionTestUtils.setField(app, "id", appId);
        when(applications.findByIdForUpdate(appId)).thenReturn(Optional.of(app));
        ApplicationDocument stored = new ApplicationDocument(app, "IDENTITY", "filesystem", UUID.randomUUID() + ".pdf",
                "identity.pdf", "application/pdf", 8L, "a".repeat(64), DocumentStatus.UPLOADED, Instant.now());
        when(documents.findFirstByApplication_IdAndDocumentTypeAndContentSha256(eq(appId), eq("IDENTITY"), anyString()))
                .thenReturn(Optional.of(stored));
        LoanApplicationService service = new LoanApplicationService(applications, users, products, history,
                documents, checks, audits, new ApplicationFileStorage(directory.toString()),
                new EmiCalculationService(), DataSize.ofMegabytes(10));

        ApplicationDocumentResponse result = service.uploadDocument(appId, "identity", new MockMultipartFile(
                "file", "identity.pdf", "text/plain", "%PDF-1.7".getBytes()));

        assertThat(result.documentType()).isEqualTo("IDENTITY");
        try (var files = Files.list(directory)) { assertThat(files.count()).isZero(); }
        verify(documents, never()).saveAndFlush(any());
    }

    @Test
    void reviewPersistsMissingDocumentChecksAndNeverApproves() {
        LoanApplicationRepository applications = mock(LoanApplicationRepository.class);
        CustomerUserRepository users = mock(CustomerUserRepository.class);
        LoanProductRepository products = mock(LoanProductRepository.class);
        ApplicationStatusHistoryRepository history = mock(ApplicationStatusHistoryRepository.class);
        ApplicationDocumentRepository documents = mock(ApplicationDocumentRepository.class);
        AgentCheckRepository checks = mock(AgentCheckRepository.class);
        AuditEventRepository audits = mock(AuditEventRepository.class);
        LoanApplication app = new LoanApplication("LA-REVIEW", new CustomerUser(null, "Demo", null, null), null,
                BigDecimal.valueOf(50000), 24, null, ApplicationStatus.DRAFT);
        UUID appId = UUID.randomUUID();
        ReflectionTestUtils.setField(app, "id", appId);
        when(applications.findByIdForUpdate(appId)).thenReturn(Optional.of(app));
        when(checks.findByApplication_IdOrderByCreatedAtDesc(eq(appId), eq(PageRequest.of(0, 3))))
                .thenReturn(Page.empty(PageRequest.of(0, 3)));
        when(checks.saveAllAndFlush(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(audits.findTopByApplication_IdAndEventTypeOrderByCreatedAtDesc(appId, "APPLICATION_REVIEW_COMPLETED"))
                .thenReturn(Optional.empty());
        LoanApplicationService service = new LoanApplicationService(applications, users, products, history,
                documents, checks, audits, new ApplicationFileStorage(directory.toString()), new EmiCalculationService(), DataSize.ofMegabytes(10));

        ApplicationReviewResponse response = service.review(appId, new ReviewRequest(
                List.of(AgentType.DOCUMENT_VERIFICATION, AgentType.CREDIT_ASSESSMENT, AgentType.COMPLIANCE), null));

        assertThat(response.recommendation()).isEqualTo("REQUEST_ADDITIONAL_DOCUMENTS");
        assertThat(response.missingDocuments()).containsExactly("IDENTITY", "ADDRESS", "INCOME");
        assertThat(response.humanApprovalRequired()).isTrue();
        assertThat(response.checks()).hasSize(3);
        assertThat(app.getApplicationStatus()).isEqualTo(ApplicationStatus.ADDITIONAL_DOCUMENTS_REQUIRED);
        verify(checks).saveAllAndFlush(anyList());
        verify(audits).saveAndFlush(any());
    }
}
