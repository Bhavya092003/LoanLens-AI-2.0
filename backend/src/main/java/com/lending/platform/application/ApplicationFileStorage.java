package com.lending.platform.application;

import com.lending.platform.common.api.ApiException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ApplicationFileStorage {
    private final Path directory;
    public ApplicationFileStorage(@Value("${app.application-storage.directory:/var/lib/lending/applications}") String directory) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }
    public String store(byte[] bytes) {
        String key = UUID.randomUUID() + "." + extension(bytes);
        Path target = directory.resolve(key).normalize();
        if (!target.getParent().equals(directory)) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FILE", "Invalid file name");
        try { Files.createDirectories(directory); Files.write(target, bytes); return key; }
        catch (IOException exception) { throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "FILE_STORAGE_FAILED", "The document could not be stored"); }
    }
    public byte[] read(String storageKey) {
        Path target = directory.resolve(storageKey).normalize();
        if (!target.getParent().equals(directory)) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FILE", "Invalid file name");
        try { return Files.readAllBytes(target); }
        catch (IOException exception) { throw new ApiException(HttpStatus.NOT_FOUND, "FILE_NOT_FOUND", "The stored document is not available"); }
    }
    public String extension(byte[] bytes) {
        if (starts(bytes, "%PDF-".getBytes())) return "pdf";
        if (starts(bytes, new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10})) return "png";
        if (bytes.length >= 3 && bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xd8 && bytes[2] == (byte) 0xff) return "jpg";
        throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE_TYPE", "Only PDF, PNG, and JPEG files are supported");
    }
    private boolean starts(byte[] value, byte[] prefix) {
        if (value.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) if (value[i] != prefix[i]) return false;
        return true;
    }
}
