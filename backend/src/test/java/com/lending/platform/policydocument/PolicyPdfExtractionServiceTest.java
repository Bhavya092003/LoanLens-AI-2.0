package com.lending.platform.policydocument;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lending.platform.common.api.ApiException;
import java.io.ByteArrayOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.junit.jupiter.api.Test;

class PolicyPdfExtractionServiceTest {
    private final PolicyPdfExtractionService service = new PolicyPdfExtractionService(20, 100_000);

    @Test
    void extractsEachPageWithOneBasedNumbersAndKeepsTextlessPages() throws Exception {
        byte[] pdf = pdf("Policy page one", null, "Policy page three");

        service.validate(pdf);
        PolicyPdfExtractionService.ExtractionResult result = service.extract(pdf);

        assertThat(result.hasText()).isTrue();
        assertThat(result.pages()).extracting(PolicyPdfExtractionService.ExtractedPage::pageNumber)
                .containsExactly(1, 2, 3);
        assertThat(result.pages()).extracting(PolicyPdfExtractionService.ExtractedPage::text)
                .containsExactly("Policy page one", "", "Policy page three");
    }

    @Test
    void acceptsValidTextlessPdfAndReportsNoExtractableText() throws Exception {
        byte[] pdf = pdf(null, null);

        service.validate(pdf);
        PolicyPdfExtractionService.ExtractionResult result = service.extract(pdf);

        assertThat(result.pages()).hasSize(2);
        assertThat(result.hasText()).isFalse();
        assertThat(result.pages()).extracting(PolicyPdfExtractionService.ExtractedPage::text)
                .containsExactly("", "");
    }

    @Test
    void rejectsNonPdfBytesEvenWhenClientUsesPdfFilename() {
        assertThatThrownBy(() -> service.validate("not a PDF".getBytes()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("valid supported PDF");
    }

    @Test
    void rejectsMalformedPdfAndPageCountOverLimit() throws Exception {
        assertThatThrownBy(() -> service.validate("%PDF-broken".getBytes()))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> new PolicyPdfExtractionService(1, 1000).validate(pdf(null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("page count");
    }

    @Test
    void rejectsEncryptedPdfWithoutAttemptingPasswordRecovery() throws Exception {
        byte[] encrypted;
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            StandardProtectionPolicy policy = new StandardProtectionPolicy("owner", "user", new AccessPermission());
            policy.setEncryptionKeyLength(128);
            document.protect(policy);
            document.save(output);
            encrypted = output.toByteArray();
        }

        assertThatThrownBy(() -> service.validate(encrypted))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Encrypted PDFs");
    }

    private byte[] pdf(String... pageTexts) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (String pageText : pageTexts) {
                PDPage page = new PDPage();
                document.addPage(page);
                if (pageText != null) {
                    try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                        stream.beginText();
                        stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                        stream.newLineAtOffset(50, 700);
                        stream.showText(pageText);
                        stream.endText();
                    }
                }
            }
            document.save(output);
            return output.toByteArray();
        }
    }
}
