package com.lending.platform.policydocument;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import com.lending.platform.common.api.ApiException;

@Service
public class PolicyPdfExtractionService {
    private static final byte[] PDF_SIGNATURE = {'%', 'P', 'D', 'F', '-'};
    private final int maxPages;
    private final int maxExtractedCharacters;

    public PolicyPdfExtractionService(
            @Value("${app.policy-storage.max-pages:500}") int maxPages,
            @Value("${app.policy-storage.max-extracted-characters:10000000}") int maxExtractedCharacters) {
        this.maxPages = maxPages;
        this.maxExtractedCharacters = maxExtractedCharacters;
    }

    public void validate(byte[] content) {
        if (content == null || content.length < PDF_SIGNATURE.length || !hasPdfSignature(content)) {
            throw invalidPdf();
        }
        try (PDDocument document = Loader.loadPDF(content)) {
            validateDocumentLimits(document);
            if (document.isEncrypted()) {
                throw encryptedPdf();
            }
        } catch (InvalidPasswordException exception) {
            throw encryptedPdf();
        } catch (IOException | IllegalArgumentException exception) {
            throw invalidPdf();
        }
    }

    public ExtractionResult extract(byte[] content) {
        try (PDDocument document = Loader.loadPDF(content)) {
            validateDocumentLimits(document);
            if (document.isEncrypted()) {
                throw new PolicyPdfProcessingException("Encrypted PDFs are not supported");
            }
            List<ExtractedPage> pages = new ArrayList<>(document.getNumberOfPages());
            int totalCharacters = 0;
            boolean hasText = false;
            for (int pageNumber = 1; pageNumber <= document.getNumberOfPages(); pageNumber++) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(pageNumber);
                stripper.setEndPage(pageNumber);
                String text = stripper.getText(document).strip();
                totalCharacters += text.length();
                if (totalCharacters > maxExtractedCharacters) {
                    throw new PolicyPdfProcessingException("Extracted policy text exceeds the configured limit");
                }
                hasText |= !text.isBlank();
                pages.add(new ExtractedPage(pageNumber, text));
            }
            return new ExtractionResult(List.copyOf(pages), hasText);
        } catch (InvalidPasswordException exception) {
            throw new PolicyPdfProcessingException("Encrypted PDFs are not supported");
        } catch (IOException | IllegalArgumentException exception) {
            throw new PolicyPdfProcessingException("PDF text extraction failed");
        }
    }

    private void validateDocumentLimits(PDDocument document) {
        int pages = document.getNumberOfPages();
        if (pages < 1 || pages > maxPages) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PDF_PAGE_LIMIT_EXCEEDED",
                    "PDF page count is outside the supported limit");
        }
    }

    private boolean hasPdfSignature(byte[] content) {
        for (int i = 0; i < PDF_SIGNATURE.length; i++) {
            if (content[i] != PDF_SIGNATURE[i]) return false;
        }
        return true;
    }

    private ApiException invalidPdf() {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PDF", "The uploaded file is not a valid supported PDF");
    }

    private ApiException encryptedPdf() {
        return new ApiException(HttpStatus.BAD_REQUEST, "ENCRYPTED_PDF", "Encrypted PDFs are not supported");
    }

    public record ExtractedPage(int pageNumber, String text) {}
    public record ExtractionResult(List<ExtractedPage> pages, boolean hasText) {}
}
