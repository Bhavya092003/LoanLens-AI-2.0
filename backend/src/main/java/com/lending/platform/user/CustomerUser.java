package com.lending.platform.user;

import com.lending.platform.common.persistence.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Locale;

@Entity
@Table(name = "users")
public class CustomerUser extends TimestampedEntity {
    @Size(max = 160)
    @Column(name = "external_reference", length = 160)
    private String externalReference;

    @NotBlank
    @Size(max = 200)
    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Email
    @Size(max = 320)
    @Column(name = "email", length = 320)
    private String email;

    @Size(max = 32)
    @Column(name = "phone", length = 32)
    private String phone;

    @Size(max = 120)
    @Column(name = "auth_username", length = 120)
    private String username;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 24)
    private UserRole role;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    protected CustomerUser() {}

    public CustomerUser(String externalReference, String fullName, String email, String phone) {
        this.externalReference = externalReference;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
    }

    public CustomerUser(String username, String passwordHash, UserRole role, String fullName, String email) {
        this.username = username.toLowerCase(Locale.ROOT);
        this.passwordHash = passwordHash;
        this.role = role;
        this.fullName = fullName;
        this.email = email;
    }

    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public UserRole getRole() { return role; }
    public boolean isEnabled() { return enabled; }
    public String getExternalReference() { return externalReference; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
}
