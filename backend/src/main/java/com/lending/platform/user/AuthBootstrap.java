package com.lending.platform.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AuthBootstrap {
    @Bean
    ApplicationRunner reviewerBootstrap(CustomerUserRepository users, PasswordEncoder encoder,
                                        @Value("${app.auth.reviewer-username:}") String username,
                                        @Value("${app.auth.reviewer-password:}") String password) {
        return args -> {
            if (username.isBlank() || password.isBlank() || users.existsByUsernameIgnoreCase(username)) return;
            users.save(new CustomerUser(username, encoder.encode(password), UserRole.REVIEWER,
                    "Configured human reviewer", null));
        };
    }
}
