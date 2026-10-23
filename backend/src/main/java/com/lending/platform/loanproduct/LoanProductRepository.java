package com.lending.platform.loanproduct;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoanProductRepository extends JpaRepository<LoanProduct, UUID> {
    @Query("select p from LoanProduct p where p.active = true "
            + "and lower(p.loanType) = lower(:loanType) "
            + "and (:amount is null or (p.minimumAmount <= :amount and p.maximumAmount >= :amount))")
    Page<LoanProduct> findActiveProducts(@Param("loanType") String loanType,
                                         @Param("amount") BigDecimal amount,
                                         Pageable pageable);

    @Query("select p from LoanProduct p where p.active = true "
            + "and (:amount is null or (p.minimumAmount <= :amount and p.maximumAmount >= :amount))")
    Page<LoanProduct> findActiveProductsWithoutType(@Param("amount") BigDecimal amount,
                                                    Pageable pageable);
}
