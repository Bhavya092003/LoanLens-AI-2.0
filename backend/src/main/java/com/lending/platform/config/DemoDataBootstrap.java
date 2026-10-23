package com.lending.platform.config;

import com.lending.platform.application.*;
import com.lending.platform.audit.*;
import com.lending.platform.loanproduct.*;
import com.lending.platform.user.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@Configuration
public class DemoDataBootstrap {
    @Bean
    ApplicationRunner demoDataRunner(CustomerUserRepository users, PasswordEncoder encoder,
                                     LoanProductRepository products, LoanApplicationRepository applications,
                                     ApplicationDocumentRepository documents, AgentCheckRepository checks,
                                     ApplicationStatusHistoryRepository history, AuditEventRepository audits,
                                     ApplicationFileStorage storage,
                                     @Value("${app.demo-data.enabled:false}") boolean enabled,
                                     @Value("${app.demo-data.customer-username:demo.customer}") String username,
                                     @Value("${app.demo-data.customer-password:DemoCustomer123!}") String password) {
        return args -> { if (enabled) seed(users, encoder, products, applications, documents, checks, history, audits, storage, username, password); };
    }

    @Transactional
    void seed(CustomerUserRepository users, PasswordEncoder encoder, LoanProductRepository products,
              LoanApplicationRepository applications, ApplicationDocumentRepository documents,
              AgentCheckRepository checks, ApplicationStatusHistoryRepository history,
              AuditEventRepository audits, ApplicationFileStorage storage, String username, String password) {
        CustomerUser customer = users.findByUsernameIgnoreCase(username).orElseGet(() ->
                users.save(new CustomerUser(username, encoder.encode(password), UserRole.CUSTOMER,
                        "Demo Customer", "demo.customer@example.com")));
        LoanProduct personal = product(products, "DEMO-PERSONAL", "Demo Personal Loan", "PERSONAL",
                "Demo unsecured personal loan for exploring the application workflow.",
                "50000", "1000000", "11.50", "18.50", 12, 60, "25000");
        LoanProduct home = product(products, "DEMO-HOME", "Demo Home Improvement Loan", "HOME_IMPROVEMENT",
                "Demo secured-style home improvement product with a longer tenure.",
                "100000", "2500000", "8.50", "13.25", 24, 120, "40000");
        seedApplication(applications, customer, personal, "LL-DEMO-REVIEW-001", ApplicationStatus.DECISION_PENDING,
                "325000", "36", "65000", documents, checks, history, audits, storage, true);
        seedApplication(applications, customer, home, "LL-DEMO-DOCS-002", ApplicationStatus.ADDITIONAL_DOCUMENTS_REQUIRED,
                "850000", "60", "90000", documents, checks, history, audits, storage, false);
    }

    private LoanProduct product(LoanProductRepository repository, String code, String name, String type,
                                String description, String minAmount, String maxAmount, String minRate,
                                String maxRate, int minTenure, int maxTenure, String minIncome) {
        return repository.findByProductCode(code).orElseGet(() -> repository.save(new LoanProduct(code, name, type,
                description, bd(minAmount), bd(maxAmount), bd(minRate), bd(maxRate), minTenure, maxTenure, bd(minIncome), true)));
    }

    private void seedApplication(LoanApplicationRepository applications, CustomerUser customer, LoanProduct product, String reference, ApplicationStatus status,
                                 String amount, String tenure, String income, ApplicationDocumentRepository documents,
                                 AgentCheckRepository checks, ApplicationStatusHistoryRepository history,
                                 AuditEventRepository audits, ApplicationFileStorage storage, boolean complete) {
        if (applications.findByApplicationReference(reference).isPresent()) return;
        LoanApplication application = applications.save(new LoanApplication(reference, customer, product, bd(amount),
                Integer.valueOf(tenure), bd(income), status));
        history.save(new ApplicationStatusHistory(application, ApplicationStatus.DRAFT, ApplicationStatus.SUBMITTED,
                customer.getId(), "Demo application submitted for reviewer processing"));
        if (complete) {
            addDocument(application, "IDENTITY", "demo-identity.pdf", documents, storage);
            addDocument(application, "ADDRESS", "demo-address.pdf", documents, storage);
            addDocument(application, "INCOME", "demo-income.pdf", documents, storage);
        } else {
            addDocument(application, "IDENTITY", "demo-identity.pdf", documents, storage);
        }
        checks.save(new AgentCheck(application, AgentType.DOCUMENT_VERIFICATION,
                complete ? CheckStatus.PASS : CheckStatus.INCOMPLETE,
                Map.of("requiredDocumentTypes", List.of("IDENTITY", "ADDRESS", "INCOME"),
                        "uploadedDocumentTypes", complete ? List.of("IDENTITY", "ADDRESS", "INCOME") : List.of("IDENTITY"),
                        "missingDocumentTypes", complete ? List.of() : List.of("ADDRESS", "INCOME")), true));
        checks.save(new AgentCheck(application, AgentType.CREDIT_ASSESSMENT, CheckStatus.NEEDS_HUMAN_REVIEW,
                Map.of("declaredMonthlyIncome", income, "message", "Indicative EMI affordability signal; human verification required"), true));
        checks.save(new AgentCheck(application, AgentType.COMPLIANCE, CheckStatus.INCOMPLETE,
                Map.of("kyc", "NOT_CONNECTED", "sanctions", "NOT_CONNECTED", "bureau", "NOT_CONNECTED"), true));
        audits.save(new AuditEvent(application, ActorType.SYSTEM, null, "APPLICATION_REVIEW_COMPLETED",
                Map.of("checkCount", 3, "demoData", true)));
    }

    private void addDocument(LoanApplication application, String type, String filename,
                             ApplicationDocumentRepository documents, ApplicationFileStorage storage) {
        byte[] content = ("%PDF-1.4\n% Demo " + type + "\n1 0 obj\n<< /Type /Catalog >>\nendobj\n%%EOF\n")
                .getBytes(StandardCharsets.US_ASCII);
        String key = storage.store(content);
        documents.save(new ApplicationDocument(application, type, "LOCAL", key, filename, "application/pdf",
                (long) content.length, null, DocumentStatus.UPLOADED, Instant.now()));
    }

    private BigDecimal bd(String value) { return new BigDecimal(value); }
}
