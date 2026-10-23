package com.lending.platform.user;

import com.lending.platform.common.api.ApiException;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final CustomerUserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthService(CustomerUserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users; this.encoder = encoder; this.jwt = jwt;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        if (users.existsByUsernameIgnoreCase(username))
            throw new ApiException(HttpStatus.CONFLICT, "USERNAME_TAKEN", "That username is already registered");
        CustomerUser user = users.saveAndFlush(new CustomerUser(username, encoder.encode(request.password()),
                UserRole.CUSTOMER, request.fullName().trim(), request.email()));
        return response(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(String username, String password) {
        CustomerUser user = users.findByUsernameIgnoreCase(username.trim()).orElse(null);
        if (user == null || !user.isEnabled() || !encoder.matches(password, user.getPasswordHash()))
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid username or password");
        return response(user);
    }

    @Transactional
    public AuthResponse createReviewer(CreateReviewerRequest request) {
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        if (users.existsByUsernameIgnoreCase(username))
            throw new ApiException(HttpStatus.CONFLICT, "USERNAME_TAKEN", "That username is already registered");
        CustomerUser user = users.saveAndFlush(new CustomerUser(username, encoder.encode(request.password()),
                UserRole.REVIEWER, request.fullName().trim(), null));
        return response(user);
    }

    private AuthResponse response(CustomerUser user) {
        return new AuthResponse(user.getId(), user.getUsername(), user.getRole().name(), jwt.issue(user));
    }
}
