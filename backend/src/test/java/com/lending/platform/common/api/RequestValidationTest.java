package com.lending.platform.common.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lending.platform.application.CreateApplicationRequest;
import com.lending.platform.discovery.EmiRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class RequestValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsInvalidEmiInputs() {
        EmiRequest request = new EmiRequest(BigDecimal.ZERO, new BigDecimal("101"), 0);

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void acceptsWellFormedApplicationRequest() {
        CreateApplicationRequest request = new CreateApplicationRequest(
                java.util.UUID.randomUUID(), null, new BigDecimal("100000.00"), 24,
                new BigDecimal("60000.00"));

        assertTrue(validator.validate(request).isEmpty());
    }
}
