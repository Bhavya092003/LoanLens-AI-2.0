package com.lending.platform.config;

import com.lending.platform.user.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import com.lending.platform.user.CustomerUserRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(cors -> {})
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/register", "/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/reviewers").hasRole("REVIEWER")
                        .requestMatchers("/api/v1/auth/me").authenticated()
                        .requestMatchers("/api/v1/applications/review-queue").hasRole("REVIEWER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/applications/*/review").hasRole("REVIEWER")
                        .requestMatchers(HttpMethod.GET, "/api/v1/applications/*/review").hasAnyRole("CUSTOMER", "REVIEWER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/applications/*/decision").hasRole("REVIEWER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/applications").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/applications/*/documents").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/applications/*/submit").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.GET, "/api/v1/applications/*").hasAnyRole("CUSTOMER", "REVIEWER")
                        .requestMatchers(HttpMethod.GET, "/api/v1/applications/*/documents").hasAnyRole("CUSTOMER", "REVIEWER")
                        .anyRequest().permitAll())
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(CustomerUserRepository users) {
        return username -> users.findByUsernameIgnoreCase(username)
                .map(user -> org.springframework.security.core.userdetails.User.withUsername(user.getUsername())
                        .password(user.getPasswordHash()).roles(user.getRole().name()).disabled(!user.isEnabled()).build())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }
}
