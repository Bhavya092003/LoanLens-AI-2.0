package com.lending.platform.policydocument;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSystemPolicyFileStorageTest {
    @TempDir
    Path directory;

    @Test
    void storesWithGeneratedKeyAndSha256AndReadsExactContent() {
        FileSystemPolicyFileStorage storage = new FileSystemPolicyFileStorage(directory.toString());
        byte[] content = "%PDF-test".getBytes();

        PolicyFileStorage.StoredPolicyFile stored = storage.store(content);

        assertThat(stored.provider()).isEqualTo("filesystem");
        assertThat(stored.storageKey()).matches("[0-9a-f-]{36}\\.pdf");
        assertThat(stored.sizeBytes()).isEqualTo(content.length);
        assertThat(stored.sha256()).hasSize(64);
        assertThat(storage.read(stored.storageKey(), 100)).containsExactly(content);
        assertThat(Files.exists(directory.resolve(stored.storageKey()))).isTrue();
    }

    @Test
    void rejectsPathTraversalAndNeverUsesClientFilenames() throws Exception {
        FileSystemPolicyFileStorage storage = new FileSystemPolicyFileStorage(directory.toString());

        assertThatThrownBy(() -> storage.read("../secret.pdf", 100))
                .isInstanceOf(PolicyStorageException.class);
        try (var storedEntries = Files.list(directory)) {
            assertThat(storedEntries.count()).isZero();
        }
    }

}
