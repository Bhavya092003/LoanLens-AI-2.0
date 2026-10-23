package com.lending.platform.loanproduct;

import com.lending.platform.common.api.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/loan-products")
public class LoanProductController {
    private final LoanProductService service;

    public LoanProductController(LoanProductService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<LoanProductResponse> list(
            @RequestParam(required = false) @Size(max = 48) String loanType,
            @RequestParam(required = false) @DecimalMin(value = "0.00") BigDecimal amount,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(loanType, amount, page, size);
    }

    @GetMapping("/{productId}")
    public ResponseEntity<LoanProductResponse> get(@PathVariable UUID productId) {
        return ResponseEntity.ok(service.get(productId));
    }
}
