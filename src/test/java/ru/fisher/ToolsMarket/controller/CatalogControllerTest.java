package ru.fisher.ToolsMarket.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ru.fisher.ToolsMarket.PostgresTestConfig;
import ru.fisher.ToolsMarket.models.Product;
import ru.fisher.ToolsMarket.repository.ProductRepository;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(initializers = PostgresTestConfig.class)
@Transactional
class CatalogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Test
    void truncatedUrlRedirectsToCanonical() throws Exception {
        productRepository.save(product("test_tovar-СКЛАД1"));

        mockMvc.perform(get("/product/test_tovar-"))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string("Location",
                        "/product/test_tovar-%D0%A1%D0%9A%D0%9B%D0%90%D0%941"));
    }

    @Test
    void ambiguousPrefixReturns404() throws Exception {
        productRepository.save(product("test_tovar-СКЛАД1"));
        productRepository.save(product("test_tovar-СКЛАД2"));

        mockMvc.perform(get("/product/test_tovar-"))
                .andExpect(status().isNotFound());
    }

    @Test
    void noMatchReturns404() throws Exception {
        mockMvc.perform(get("/product/no-such-product-"))
                .andExpect(status().isNotFound());
    }

    private Product product(String title) {
        Product p = new Product();
        p.setTitle(title);
        p.setSku("SKU-" + title.hashCode());
        p.setName("Test");
        p.setPrice(BigDecimal.ONE);
        p.setActive(true);
        return p;
    }
}
