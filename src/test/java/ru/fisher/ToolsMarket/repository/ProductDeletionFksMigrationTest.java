package ru.fisher.ToolsMarket.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import ru.fisher.ToolsMarket.PostgresTestConfig;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Правила внешних ключей на product_id проверяем отдельно от поведения:
 * ProductDeletionWithOrdersTest проходит и при nullable + NO ACTION, а
 * испорченное правило удаления вернётся молча. V22 снимает NOT NULL с
 * order_item.product_id и переводит FK корзины в CASCADE.
 */
@SpringBootTest
@ContextConfiguration(initializers = PostgresTestConfig.class)
class ProductDeletionFksMigrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void orderItemProductIdIsNullable() {
        assertThat(columnIsNullable("order_item", "product_id")).isTrue();
    }

    @Test
    void orderItemProductFksSetsNullOnDelete() {
        assertThat(deleteRule("order_item", "product_id")).isEqualTo("n");
    }

    @Test
    void cartItemProductFksCascadesOnDelete() {
        assertThat(deleteRule("cart_item", "product_id")).isEqualTo("c");
    }

    private boolean columnIsNullable(String table, String column) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT is_nullable = 'YES'
                FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = ? AND column_name = ?
                """, new Object[]{table, column}, Boolean.class));
    }

    /** confdeltype: a — NO ACTION, r — RESTRICT, c — CASCADE, n — SET NULL, d — SET DEFAULT */
    private String deleteRule(String table, String column) {
        return jdbc.queryForObject("""
                SELECT c.confdeltype
                FROM pg_constraint c
                JOIN pg_class t ON t.oid = c.conrelid
                JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = c.conkey[1]
                WHERE t.relname = ?
                  AND c.contype = 'f'
                  AND a.attname = ?
                """, new Object[]{table, column}, String.class);
    }
}
