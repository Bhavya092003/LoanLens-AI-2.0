package com.lending.platform.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lending.platform.common.api.ApiException;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApplicationFileStorageTest {
    @TempDir java.nio.file.Path temporaryDirectory;

    @Test
    void detectsSupportedMagicBytesAndUsesGeneratedKeys() {
        ApplicationFileStorage storage = new ApplicationFileStorage(temporaryDirectory.toString());
        byte[] pdf = "%PDF-1.7".getBytes();
        assertThat(storage.extension(pdf)).isEqualTo("pdf");
        String key = storage.store(pdf);
        assertThat(key).endsWith(".pdf").doesNotContain("/").doesNotContain("..");
        assertThat(Files.exists(temporaryDirectory.resolve(key))).isTrue();
    }

    @Test
    void rejectsUnknownActualContent() {
        ApplicationFileStorage storage = new ApplicationFileStorage(temporaryDirectory.toString());
        assertThatThrownBy(() -> storage.extension("not a PDF".getBytes())).isInstanceOf(ApiException.class)
                .hasMessageContaining("Only PDF, PNG, and JPEG");
    }
}
