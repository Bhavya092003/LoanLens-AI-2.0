package com.lending.platform.loanproduct;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false"})
@Testcontainers(disabledWithoutDocker = true)
class LoanProductRepositoryIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private LoanProductRepository repository;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        repository.saveAll(List.of(
                product("ACTIVE-B", "Beta", "AUTO", true, "1000", "10000"),
                product("INACTIVE", "Hidden", "AUTO", false, "1000", "10000"),
                product("ACTIVE-A", "Alpha", "PERSONAL", true, "0", "5000")));
        repository.flush();
    }

    @Test
    void returnsOnlyActiveProductsWithStablePagedOrdering() {
        Page<LoanProduct> first = repository.findActiveProducts(null, null,
                PageRequest.of(0, 1, Sort.by(Sort.Order.asc("productName"), Sort.Order.asc("id"))));
        Page<LoanProduct> second = repository.findActiveProducts(null, null,
                PageRequest.of(1, 1, Sort.by(Sort.Order.asc("productName"), Sort.Order.asc("id"))));

        assertThat(first.getTotalElements()).isEqualTo(2);
        assertThat(first.getContent()).extracting(LoanProduct::getProductName).containsExactly("Alpha");
        assertThat(second.getContent()).extracting(LoanProduct::getProductName).containsExactly("Beta");
    }

    @Test
    void filtersByLoanTypeAndAmountBounds() {
        Page<LoanProduct> results = repository.findActiveProducts("auto", new BigDecimal("5000.00"),
                PageRequest.of(0, 20, Sort.by("productName")));
        assertThat(results.getContent()).extracting(LoanProduct::getProductCode).containsExactly("ACTIVE-B");
    }

    private LoanProduct product(String code, String name, String type, boolean active, String min, String max) {
        return new LoanProduct(code, name, type, null, new BigDecimal(min), new BigDecimal(max),
                new BigDecimal("0"), new BigDecimal("20"), 1, 60, null, active);
    }
}
