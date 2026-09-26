package ru.fisher.ToolsMarket.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;

import ru.fisher.ToolsMarket.PostgresTestConfig;
import ru.fisher.ToolsMarket.dto.ProductDTO.ProductListDto;
import ru.fisher.ToolsMarket.models.Product;
import ru.fisher.ToolsMarket.models.ProductType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Публичный поиск по каталогу: полнотекстовый вектор вместо
 * LOWER(...) LIKE '%q%' с OR по четырём колонкам.
 */
@SpringBootTest
@ContextConfiguration(initializers = PostgresTestConfig.class)
class ProductSearchFtsTest {

    @Autowired
    private ProductService productService;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setup() {
        save("Дрель-шуруповёрт аккумуляторный", "DREIL-SH-18",
                "Два аккумулятора в комплекте, подсветка рабочей зоны");
        save("ПерфораторSDS-plus 800 Вт", "PERF-800",
                "Режим сверления с ударом, кейс");
        save("Тепловая пушка 3 кВт 220В", "TEPL-3KW",
                "Обогреватель для помещений до 30 квадратных метров");
    }

    private void save(String name, String sku, String description) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        productService.saveEntity(Product.builder()
                .name(name)
                .title("fts-" + suffix)
                .sku(sku + "-" + suffix.substring(0, 4))
                .price(new BigDecimal("1500.00"))
                .currency("RUB")
                .shortDescription(description)
                .description(description)
                .productType(ProductType.TOOL)
                .active(true)
                .views(0L)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .images(new LinkedHashSet<>())
                .categories(new LinkedHashSet<>())
                .attributeValues(new LinkedHashSet<>())
                .build());
    }

    @AfterEach
    void cleanup() {
        jdbc.update("TRUNCATE product_image, product_category, product_attribute_values, product,"
                + " category, users RESTART IDENTITY CASCADE");
    }

    private List<ProductListDto> search(String query) {
        return productService.searchWithDiscounts(query, null, PageRequest.of(0, 12)).getContent();
    }

    @Test
    void findsByName() {
        assertThat(search("Дрель-шуруповёрт"))
                .extracting(ProductListDto::getName)
                .contains("Дрель-шуруповёрт аккумуляторный");
    }

    @Test
    void findsByNameIgnoringCase() {
        assertThat(search("дрель")).hasSize(1);
        assertThat(search("ДРЕЛЬ")).hasSize(1);
    }

    @Test
    void findsByWordPrefix() {
        // ввод короче слова, как у поиска с автодополнением
        assertThat(search("перфораторSDS"))
                .extracting(ProductListDto::getName)
                .contains("ПерфораторSDS-plus 800 Вт");
    }

    @Test
    void findsByDescription() {
        assertThat(search("кейс"))
                .extracting(ProductListDto::getName)
                .contains("ПерфораторSDS-plus 800 Вт");
    }

    @Test
    void findsBySkuPrefix() {
        assertThat(search("TEPL-3KW"))
                .extracting(ProductListDto::getName)
                .contains("Тепловая пушка 3 кВт 220В");
    }

    @Test
    void allWordsMustMatch() {
        assertThat(search("тепловая пушка")).hasSize(1);
        assertThat(search("тепловая перфоратор")).isEmpty();
    }

    @Test
    void punctuationDoesNotBreakSearch() {
        assertThat(search("дрель & перфоратор | \"тепловая\"")).isEmpty();
        assertThat(search("3 кВт 220В")).hasSize(1);
    }

    @Test
    void garbageInputReturnsNoResults() {
        assertThat(search("%%%")).isEmpty();
        assertThat(search("   ")).isEmpty();
        assertThat(search(null)).isEmpty();
    }

    @Test
    void ginIndexExists() {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM pg_indexes WHERE indexname = 'idx_product_search_vector'",
                Integer.class);

        assertThat(count).isEqualTo(1);
    }

    @Test
    void searchVectorIsFilledByDatabase() {
        String vector = jdbc.queryForObject(
                "SELECT search_vector::text FROM product WHERE search_vector IS NOT NULL LIMIT 1",
                String.class);

        assertThat(vector).isNotNull();
        assertThat(vector).contains("дрел");
    }
}
