package com.lending.platform.loanproduct;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lending.platform.common.api.ResourceNotFoundException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

class LoanProductServiceTest {
    private final LoanProductRepository repository = org.mockito.Mockito.mock(LoanProductRepository.class);
    private final LoanProductService service = new LoanProductService(repository);

    @Test
    void listsThroughRepositoryWithRequestedPaginationAndStableSort() {
        LoanProduct product = product();
        when(repository.findActiveProducts(any(), any(), any()))
                .thenAnswer(invocation -> new PageImpl<>(List.of(product), invocation.getArgument(2), 1));

        var result = service.list("AUTO", new BigDecimal("1"), 2, 10);

        assertThat(result.content()).hasSize(1);
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.totalElements()).isEqualTo(21);
        verify(repository).findActiveProducts(org.mockito.ArgumentMatchers.eq("AUTO"),
                org.mockito.ArgumentMatchers.eq(new BigDecimal("1")),
                org.mockito.ArgumentMatchers.eq(PageRequest.of(2, 10,
                        Sort.by(Sort.Order.asc("productName"), Sort.Order.asc("id")))));
    }

    @Test
    void retrievesAnExistingProduct() {
        LoanProduct product = product();
        when(repository.findById(any())).thenReturn(Optional.of(product));
        assertThat(service.get(UUID.randomUUID()).productCode()).isEqualTo("DEMO-CODE");
    }

    @Test
    void listsWithoutLoanTypeUsingTheNullSafeRepositoryQuery() {
        when(repository.findActiveProductsWithoutType(any(), any()))
                .thenReturn(new PageImpl<>(List.of(product())));

        var result = service.list(null, null, 0, 20);

        assertThat(result.content()).hasSize(1);
        verify(repository).findActiveProductsWithoutType(
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq(PageRequest.of(0, 20,
                        Sort.by(Sort.Order.asc("productName"), Sort.Order.asc("id")))));
    }

    @Test
    void throwsNotFoundForUnknownProduct() {
        when(repository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(UUID.randomUUID())).isInstanceOf(ResourceNotFoundException.class);
    }

    private LoanProduct product() {
        return new LoanProduct("DEMO-CODE", "Illustrative product", "AUTO", "Test-only fixture",
                new BigDecimal("0.00"), new BigDecimal("500000.00"), new BigDecimal("0.0"),
                new BigDecimal("20.0"), 1, 120, null, true);
    }
}
