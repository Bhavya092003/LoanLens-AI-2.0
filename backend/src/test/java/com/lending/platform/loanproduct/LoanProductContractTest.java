package com.lending.platform.loanproduct;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lending.platform.common.api.ResourceNotFoundException;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(LoanProductController.class)
@AutoConfigureMockMvc(addFilters = false)
class LoanProductContractTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LoanProductService service;

    @Test
    void getProductReturnsExistingCatalogueResponse() throws Exception {
        UUID productId = UUID.randomUUID();
        when(service.get(productId)).thenReturn(new LoanProductResponse(productId, "ILLUSTRATIVE-01",
                "Illustrative product", "AUTO", "Illustrative test fixture", new BigDecimal("1000.00"),
                new BigDecimal("50000.00"), new BigDecimal("8.0"), new BigDecimal("12.0"),
                12, 60, null, true));

        mockMvc.perform(get("/api/v1/loan-products/{productId}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productCode").value("ILLUSTRATIVE-01"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void missingProductUsesStandardNotFoundEnvelope() throws Exception {
        UUID productId = UUID.randomUUID();
        when(service.get(productId)).thenThrow(new ResourceNotFoundException("Loan product"));

        mockMvc.perform(get("/api/v1/loan-products/{productId}", productId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void invalidPageSizeUsesStandardValidationEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/loan-products").param("page", "0").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }
}
