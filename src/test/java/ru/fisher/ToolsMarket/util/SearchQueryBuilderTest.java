package ru.fisher.ToolsMarket.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Пользовательский ввод превращается в безопасный tsquery: спецсимволы
 * синтаксиса PostgreSQL не должны попадать в запрос.
 */
class SearchQueryBuilderTest {

    @Test
    void singleWordBecomesPrefixQuery() {
        assertThat(SearchQueryBuilder.toTsQuery("дрель")).isEqualTo("дрель:*");
    }

    @Test
    void wordsAreCombinedWithAnd() {
        assertThat(SearchQueryBuilder.toTsQuery("тепловая пушка"))
                .isEqualTo("тепловая:* & пушка:*");
    }

    @Test
    void punctuationAndExtraSpacesAreIgnored() {
        assertThat(SearchQueryBuilder.toTsQuery("  Болт,   М8 (DIN)  "))
                .isEqualTo("болт:* & м8:* & din:*");
    }

    @Test
    void numbersAreKept() {
        assertThat(SearchQueryBuilder.toTsQuery("3 кВт 220")).isEqualTo("3:* & квт:* & 220:*");
    }

    @Test
    void tsquerySyntaxIsStripped() {
        // Ввод вида "& ! | :* () '" не должен ломать to_tsquery
        assertThat(SearchQueryBuilder.toTsQuery("дрель & !дрель:*")).isEqualTo("дрель:*");
        assertThat(SearchQueryBuilder.toTsQuery("a | b")).isEqualTo("a:* & b:*");
        assertThat(SearchQueryBuilder.toTsQuery("перфоратор:*")).isEqualTo("перфоратор:*");
    }

    @Test
    void inputWithoutWordsGivesEmptyQuery() {
        assertThat(SearchQueryBuilder.toTsQuery("")).isEmpty();
        assertThat(SearchQueryBuilder.toTsQuery("   ")).isEmpty();
        assertThat(SearchQueryBuilder.toTsQuery(null)).isEmpty();
        assertThat(SearchQueryBuilder.toTsQuery("%%%")).isEmpty();
        assertThat(SearchQueryBuilder.toTsQuery("()|&!*")).isEmpty();
    }

    @Test
    void duplicatedWordsAreNotRepeated() {
        assertThat(SearchQueryBuilder.toTsQuery("дрель дрель")).isEqualTo("дрель:*");
    }
}
