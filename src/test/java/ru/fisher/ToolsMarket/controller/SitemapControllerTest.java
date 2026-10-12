package ru.fisher.ToolsMarket.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.fisher.ToolsMarket.PostgresTestConfig;
import ru.fisher.ToolsMarket.service.CategoryService;
import ru.fisher.ToolsMarket.service.ProductService;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(initializers = PostgresTestConfig.class)
class SitemapControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CacheManager cacheManager;

    @MockitoBean
    private ProductService productService;

    @MockitoBean
    private CategoryService categoryService;

    @BeforeEach
    void evictSitemapCache() {
        Cache cache = cacheManager.getCache("sitemap");
        if (cache != null) {
            cache.clear();
        }
    }

    @Test
    void sitemapPercentEncodesCyrillicTitle() throws Exception {
        when(categoryService.findAllForSitemap()).thenReturn(List.of());
        when(productService.findAllForSitemap()).thenReturn(List.<Object[]>of(
                new Object[]{"test_tovar-СКЛАД1", Instant.now(), Instant.now()}
        ));

        mockMvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "/product/test_tovar-%D0%A1%D0%9A%D0%9B%D0%90%D0%941")));
    }
}
