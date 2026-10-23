package com.lending.platform.policydocument;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

@Component
public class OcrSpaceProvider {
    private final String apiKey;
    private final String language;
    private final int engine;
    private final RestClient client;

    public OcrSpaceProvider(
            @Value("${app.ocr.api-key:}") String apiKey,
            @Value("${app.ocr.api-url}") String apiUrl,
            @Value("${app.ocr.language:eng}") String language,
            @Value("${app.ocr.engine:2}") int engine,
            @Value("${app.ocr.timeout:20s}") Duration timeout) {
        this.apiKey = apiKey;
        this.language = language;
        this.engine = engine;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        this.client = RestClient.builder()
                .baseUrl(apiUrl)
                .requestFactory(requestFactory)
                .build();
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @SuppressWarnings("unchecked")
    public List<PolicyPdfExtractionService.ExtractedPage> extract(byte[] pdf, int pageCount) {
        if (!isConfigured()) {
            throw new OcrProviderException("OCR provider is not configured");
        }
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("apikey", apiKey);
        form.add("language", language);
        form.add("isOverlayRequired", "false");
        form.add("OCREngine", Integer.toString(engine));
        form.add("filetype", "PDF");
        form.add("file", new PdfResource(pdf));

        try {
            Map<String, Object> response = client.post()
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
            if (response == null) {
                throw new OcrProviderException("OCR provider returned an empty response");
            }
            if (isTrue(response.get("IsErroredOnProcessing"))) {
                throw new OcrProviderException("OCR provider reported a processing error");
            }
            Object rawResults = response.get("ParsedResults");
            if (!(rawResults instanceof List<?> parsedResults)) {
                throw new OcrProviderException("OCR provider returned no parsed results");
            }

            List<PolicyPdfExtractionService.ExtractedPage> pages = new ArrayList<>(pageCount);
            for (int pageNumber = 1; pageNumber <= pageCount; pageNumber++) {
                String text = "";
                if (pageNumber <= parsedResults.size() && parsedResults.get(pageNumber - 1) instanceof Map<?, ?> result) {
                    Object parsedText = result.get("ParsedText");
                    if (parsedText instanceof String value) {
                        text = value.strip();
                    }
                }

                pages.add(new PolicyPdfExtractionService.ExtractedPage(pageNumber, text));
            }
            return List.copyOf(pages);
        } catch (RestClientException | ClassCastException exception) {
            throw new OcrProviderException("OCR provider request failed", exception);
        }
    }

    private boolean isTrue(Object value) {
        return value instanceof Boolean booleanValue && booleanValue
                || value instanceof String stringValue && Boolean.parseBoolean(stringValue);
    }

    private static final class PdfResource extends ByteArrayResource {
        private PdfResource(byte[] content) {
            super(content);
        }

        @Override
        public String getFilename() {
            return "document.pdf";
        }

        @Override
        public long contentLength() {
            return getByteArray().length;
        }
    }
}
