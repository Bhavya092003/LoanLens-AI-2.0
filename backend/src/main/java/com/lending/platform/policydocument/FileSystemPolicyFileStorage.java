package com.lending.platform.policydocument;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FileSystemPolicyFileStorage implements PolicyFileStorage {
    private static final String PROVIDER_KEY_PATTERN = "[0-9a-fA-F-]{36}\\.pdf";
    private final Path root;

    public FileSystemPolicyFileStorage(@Value("${app.policy-storage.directory:/var/lib/lending/policies}") String directory) {
        this.root = Path.of(directory).toAbsolutePath().normalize();
    }

    @Override
    public StoredPolicyFile store(byte[] content) {
        String key = UUID.randomUUID() + ".pdf";
        Path temporary = root.resolve("." + UUID.randomUUID() + ".upload").normalize();
        Path target = resolveKey(key);
        if (!temporary.startsWith(root) || !target.startsWith(root)) {
            throw new PolicyStorageException("Storage key is invalid");
        }
        boolean temporaryCreated = false;
        try {
            Files.createDirectories(root);
            Files.write(temporary, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            temporaryCreated = true;
            // Without REPLACE_EXISTING, a key collision cannot overwrite an older version.
            Files.move(temporary, target);
            return new StoredPolicyFile(provider(), key, content.length, sha256(content));
        } catch (IOException exception) {
            if (temporaryCreated) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // A uniquely named partial file is safer to leave for operator cleanup than mask the original failure.
                }
            }
            throw new PolicyStorageException("Policy file storage failed");
        }
    }

    @Override
    public String provider() {
        return "filesystem";
    }

    @Override
    public byte[] read(String storageKey, long maxBytes) {
        try {
            Path storedFile = resolveKey(storageKey);
            if (Files.size(storedFile) > maxBytes) {
                throw new PolicyStorageException("Stored policy file exceeds the configured size limit");
            }
            return Files.readAllBytes(storedFile);
        } catch (PolicyStorageException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new PolicyStorageException("Stored policy file is unavailable");
        }
    }

    private Path resolveKey(String key) {
        if (key == null || !key.matches(PROVIDER_KEY_PATTERN)) {
            throw new PolicyStorageException("Storage key is invalid");
        }
        Path resolved = root.resolve(key).normalize();
        if (!resolved.startsWith(root) || resolved.equals(root)) {
            throw new PolicyStorageException("Storage key is invalid");
        }
        return resolved;
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable");
        }
    }
}
