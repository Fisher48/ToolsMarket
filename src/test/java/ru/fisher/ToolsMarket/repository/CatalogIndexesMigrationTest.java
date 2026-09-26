package ru.fisher.ToolsMarket.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;

import ru.fisher.ToolsMarket.PostgresTestConfig;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Миграции с индексами применяются один раз и повторно не проверяются, поэтому
 * опечатка в имени или в имени колонки всплыла бы при первом же применении, а
 * не в тестах — потому и проверяем, что Flyway действительно создал то, что нужно.
 */
@SpringBootTest
@ContextConfiguration(initializers = PostgresTestConfig.class)
class CatalogIndexesMigrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void catalogIndexesExist() {
        assertThat(indexes()).contains(
                "idx_product_category_category_product",
                "idx_product_image_product_sort",
                "idx_cart_item_cart_product",
                "idx_orders_user_created",
                "idx_product_search_vector");
    }

    @Test
    void catalogIndexColumnsMatchQueries() {
        assertThat(indexDef("idx_product_category_category_product"))
                .contains("(category_id, product_id)");
        assertThat(indexDef("idx_product_image_product_sort"))
                .contains("(product_id, sort_order)");
        assertThat(indexDef("idx_cart_item_cart_product"))
                .contains("(cart_id, product_id)");
        assertThat(indexDef("idx_orders_user_created"))
                .contains("(user_id, created_at DESC)");
    }

    @Test
    void redundantIndexesAreDropped() {
        // UNIQUE(user_type, product_type) и (product_id, sort_order) покрывают их
        assertThat(indexes()).doesNotContain(
                "idx_user_discounts_user_type",
                "idx_productimage_product");

        // а нужные индексы на их месте остались
        assertThat(indexes()).contains(
                "idx_user_discounts_product_type",
                "idx_product_image_product_sort");
    }

    @Test
    void fullTextSearchUsesGinIndex() {
        assertThat(indexDef("idx_product_search_vector"))
                .contains("USING gin")
                .contains("search_vector");
    }

    private java.util.List<String> indexes() {
        return jdbc.queryForList(
                "SELECT indexname FROM pg_indexes WHERE schemaname = 'public'", String.class);
    }

    private String indexDef(String indexName) {
        return jdbc.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE schemaname = 'public' AND indexname = ?",
                new Object[]{indexName}, String.class);
    }
}
