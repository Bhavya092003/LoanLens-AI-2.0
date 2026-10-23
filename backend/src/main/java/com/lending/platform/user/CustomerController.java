package com.lending.platform.user;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {
    private final CustomerUserRepository users;
    public CustomerController(CustomerUserRepository users) { this.users = users; }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse create(@Valid @RequestBody CreateCustomerRequest request) {
        CustomerUser user = users.save(new CustomerUser(null, request.fullName(), request.email(), request.phone()));
        return new CustomerResponse(user.getId(), user.getFullName(), user.getEmail());
    }
    public record CustomerResponse(UUID userId, String fullName, String email) {}
}
