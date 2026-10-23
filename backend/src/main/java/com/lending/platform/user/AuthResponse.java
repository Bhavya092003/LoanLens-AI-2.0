package com.lending.platform.user;

import java.util.UUID;

public record AuthResponse(UUID userId, String username, String role, String token) {
}
