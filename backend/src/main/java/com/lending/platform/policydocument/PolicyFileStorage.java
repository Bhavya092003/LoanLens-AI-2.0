package com.lending.platform.policydocument;

public interface PolicyFileStorage {
    StoredPolicyFile store(byte[] content);
    byte[] read(String storageKey, long maxBytes);
    String provider();

    record StoredPolicyFile(String provider, String storageKey, long sizeBytes, String sha256) {}
}
