package com.lending.platform.policyqa;

import java.time.LocalDate;
import java.util.List;

public record PolicyQuestionResponse(String answer, PolicyConfidence confidence,
                                     List<Source> sources, boolean followUpRequired) {
    public record Source(String document, String section, String version, LocalDate effectiveDate,
                         Integer page, java.util.UUID documentId, java.util.UUID chunkId) {}
}
