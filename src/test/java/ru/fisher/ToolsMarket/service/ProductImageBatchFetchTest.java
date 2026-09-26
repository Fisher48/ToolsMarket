package ru.fisher.ToolsMarket.service;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;

import ru.fisher.ToolsMarket.PostgresTestConfig;
import ru.fisher.ToolsMarket.models.Category;
import ru.fisher.ToolsMarket.models.Product;
import ru.fisher.ToolsMarket.models.ProductImage;
import ru.fisher.ToolsMarket.models.ProductType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Страница поиска отдаёт до 12 товаров, и маппер дёргает getImages() на каждом.
 * Проверяем в свежей сессии Hibernate, что картинки грузятся батчем, а не
 * отдельным запросом на товар (N+1).
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@ContextConfiguration(initializers = PostgresTestConfig.class)
class ProductImageBatchFetchTest {

    private static final int PRODUCTS = 6;

    @Autowired
    private ProductService productService;
    @Autowired
    private CategoryService categoryService;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private SessionFactory sessionFactory;

    private String query;

    @BeforeEach
    void setup() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        query = "batch-" + suffix;

        Category category = categoryService.saveEntity(Category.builder()
                .title("batch-" + suffix)
                .name("Категория " + suffix)
                .description("Описание")
                .sortOrder(0)
                .createdAt(Instant.now())
                .build());

        for (int i = 0; i < PRODUCTS; i++) {
            Product product = Product.builder()
                    .name(query + "-товар-" + i)
                    .title("title-" + suffix + "-" + i)
                    .sku("SKU-" + suffix + "-" + i)
                    .price(new BigDecimal("1000.00").add(new BigDecimal(i)))
                    .currency("RUB")
                    .shortDescription("Короткое описание " + i)
                    .description("Полное описание " + i)
                    .productType(ProductType.TOOL)
                    .active(true)
                    .views(0L)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .images(new LinkedHashSet<>())
                    .categories(new HashSet<>(Set.of(category)))
                    .attributeValues(new LinkedHashSet<>())
                    .build();

            Product saved = productService.saveEntity(product);

            for (int img = 0; img < 2; img++) {
                ProductImage image = ProductImage.builder()
                        .url("/images/" + suffix + "-" + i + "-" + img + ".jpg")
                        .alt("alt " + img)
                        .sortOrder(img + 1)
                        .build();
                image.setProduct(saved);
                saved.getImages().add(image);
            }

            productService.saveEntity(saved);
        }
    }

    @AfterEach
    void cleanup() {
        jdbc.update("TRUNCATE product_image, product_category, product_attribute_values, product,"
                + " category, users RESTART IDENTITY CASCADE");
    }

    @Test
    void imagesOfSearchPageAreLoadedInOneQuery() {
        Statistics statistics = sessionFactory.getStatistics();
        statistics.clear();

        long statements;
        int productCount;
        int imageCount;
        try (Session session = sessionFactory.openSession()) {
            Transaction tx = session.beginTransaction();

            List<Product> products = session.createQuery(
                            "SELECT p FROM Product p WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%'))",
                            Product.class)
                    .setParameter("q", query)
                    .getResultList();

            // маппер страницы поиска обращается к картинкам каждого товара
            products.forEach(p -> p.getImages().size());
            productCount = products.size();
            imageCount = products.stream().mapToInt(p -> p.getImages().size()).sum();
            statements = statistics.getPrepareStatementCount();

            tx.commit();
        }

        assertThat(productCount).isEqualTo(PRODUCTS);
        assertThat(imageCount).isEqualTo(PRODUCTS * 2);
        // @BatchSize(size = 50): 1 запрос на товары + 1 на все картинки.
        // Без батча было бы 1 + 6 = 7 запросов на страницу
        assertThat(statements)
                .as("SQL-запросов на загрузку страницы поиска с картинками")
                .isLessThanOrEqualTo(3);
    }
}
