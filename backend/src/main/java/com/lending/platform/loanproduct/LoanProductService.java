package com.lending.platform.loanproduct;

import com.lending.platform.common.api.PageResponse;
import com.lending.platform.common.api.ResourceNotFoundException;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoanProductService {
    private final LoanProductRepository repository;

    public LoanProductService(LoanProductRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PageResponse<LoanProductResponse> list(String loanType, BigDecimal amount, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Order.asc("productName"), Sort.Order.asc("id")));
        var products = loanType == null || loanType.isBlank()
                ? repository.findActiveProductsWithoutType(amount, pageable)
                : repository.findActiveProducts(loanType, amount, pageable);
        return PageResponse.from(products, LoanProductResponse::from);
    }

    @Transactional(readOnly = true)
    public LoanProductResponse get(UUID productId) {
        return repository.findById(productId).map(LoanProductResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Loan product"));
    }
}
