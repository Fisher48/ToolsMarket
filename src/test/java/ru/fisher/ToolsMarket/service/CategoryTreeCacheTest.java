package ru.fisher.ToolsMarket.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;

import ru.fisher.ToolsMarket.PostgresTestConfig;
import ru.fisher.ToolsMarket.config.CacheConfig;
import ru.fisher.ToolsMarket.dto.CategoryDTO.CategoryDto;
import ru.fisher.ToolsMarket.models.Category;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Дерево категорий кэшируется: меню каталога собирается в @ModelAttribute на
 * каждом запросе, и без кэша это запрос к БД на каждую страницу.
 */
@SpringBootTest
@ContextConfiguration(initializers = PostgresTestConfig.class)
class CategoryTreeCacheTest {

    @Autowired
    private CategoryService categoryService;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void setup() {
        clearCache();
        createCategory("root-" + UUID.randomUUID().toString().substring(0, 8));
    }

    @AfterEach
    void cleanup() {
        clearCache();
        jdbc.update("TRUNCATE product_category, product_image, product_attribute_values, product,"
                + " category, users RESTART IDENTITY CASCADE");
    }

    private void clearCache() {
        Cache cache = cacheManager.getCache(CacheConfig.CATEGORY_TREE);
        if (cache != null) {
            cache.clear();
        }
    }

    private Category createCategory(String title) {
        return categoryService.saveEntity(Category.builder()
                .title(title)
                .name("Категория " + title)
                .description("Описание")
                .sortOrder(0)
                .createdAt(Instant.now())
                .build());
    }

    @Test
    void cacheNameIsRegistered() {
        // Имя кэша перечислено в CaffeineCacheManager статически: без регистрации
        // @Cacheable упал бы с IllegalArgumentException в рантайме
        assertThat(cacheManager.getCacheNames()).contains(CacheConfig.CATEGORY_TREE);
    }

    @Test
    void repeatedCallsAreServedFromCache() {
        List<CategoryDto> first = categoryService.getRootCategories();
        List<CategoryDto> second = categoryService.getRootCategories();

        assertThat(first).isNotEmpty();
        // тот же объект из кэша — запрос в БД не повторялся
        assertThat(second).isSameAs(first);
    }

    @Test
    void newCategoryAppearsAfterCacheEvict() {
        List<CategoryDto> before = categoryService.getRootCategories();
        assertThat(before).isNotEmpty();

        createCategory("second-" + UUID.randomUUID().toString().substring(0, 8));

        List<CategoryDto> after = categoryService.getRootCategories();
        assertThat(after).hasSize(before.size() + 1);
    }

    @Test
    void deletedCategoryDisappearsAfterCacheEvict() {
        Category extra = createCategory("to-delete-" + UUID.randomUUID().toString().substring(0, 8));

        List<CategoryDto> before = categoryService.getRootCategories();
        assertThat(before).hasSize(2);

        categoryService.deleteEntity(extra.getId());

        assertThat(categoryService.getRootCategories()).hasSize(1);
    }

    @Test
    void homeCategoriesAreCachedSeparately() {
        List<CategoryDto> home = categoryService.getParentCategoriesForHome();
        assertThat(home).isNotEmpty();
        assertThat(categoryService.getParentCategoriesForHome()).isSameAs(home);
    }
}
