package com.wind.config;

import java.util.Set;

public class ApiKeyStore {
    // Conjunto de chaves de API válidas.
    private static final Set<String> VALID_API_KEYS = Set.of(
        "super-secret-key-123",
        "another-valid-key-456"
    );

    public boolean isValid(String apiKey) {
        return apiKey != null && VALID_API_KEYS.contains(apiKey);
    }
}