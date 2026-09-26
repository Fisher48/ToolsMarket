package ru.fisher.ToolsMarket.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;

import ru.fisher.ToolsMarket.PostgresTestConfig;
import ru.fisher.ToolsMarket.dto.CategoryDTO.CategoryPageData;
import ru.fisher.ToolsMarket.dto.ProductDTO.ProductCardDto;
import ru.fisher.ToolsMarket.models.Category;
import ru.fisher.ToolsMarket.models.Product;
import ru.fisher.ToolsMarket.models.User;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Страница категории: страница не должна создавать корзину пользователю,
 * а количество товаров в карточке должно совпадать с реальной корзиной.
 */
@SpringBootTest
@ContextConfiguration(initializers = PostgresTestConfig.class)
class CategoryServiceTest {

    @Autowired
    private CategoryService categoryService;
    @Autowired
    private ProductService productService;
    @Autowired
    private CartService cartService;
    @Autowired
    private UserService userService;
    @Autowired
    private JdbcTemplate jdbc;

    private User user;
    private Category category;
    private Product inCartProduct;

    @BeforeEach
    void setup() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        user = userService.createAdminUser("cat_" + suffix, "cat_" + suffix + "@example.com", "password");
        user = userService.findByUsername("cat_" + suffix).orElseThrow();

        category = categoryService.saveEntity(Category.builder()
                .title("cat-" + suffix)
                .name("Категория " + suffix)
                .description("Описание")
                .sortOrder(0)
                .createdAt(Instant.now())
                .build());

        inCartProduct = saveProduct("Товар в корзине " + suffix, "3000.00");
        saveProduct("Товар вне корзины " + suffix, "1000.00");
    }

    private Product saveProduct(String name, String price) {
        return productService.saveEntity(Product.builder()
                .name(name)
                .title("title-" + UUID.randomUUID().toString().substring(0, 8))
                .sku("SKU-" + UUID.randomUUID().toString().substring(0, 8))
                .price(new BigDecimal(price))
                .currency("RUB")
                .shortDescription("Короткое описание")
                .description("Полное описание")
                .active(true)
                .views(0L)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .images(new java.util.LinkedHashSet<>())
                .categories(new HashSet<>(Set.of(category)))
                .attributeValues(new java.util.LinkedHashSet<>())
                .build());
    }

    @AfterEach
    void clean() {
        jdbc.update("TRUNCATE cart_item, cart, product_category, product_image, product_attribute_values,"
                + " attribute, product, category, users RESTART IDENTITY CASCADE");
    }

    @Test
    void categoryPageDoesNotCreateCartRow() {
        long cartsBefore = countCarts();

        CategoryPageData pageData = categoryService.getCategoryPage(
                category.getTitle(), user.getId(), "name_asc", 0, 12);

        assertThat(pageData.getProducts().getContent()).isNotEmpty();
        assertThat(countCarts()).isEqualTo(cartsBefore);
    }

    @Test
    void categoryPageReportsCartQuantitiesFromPageRows() {
        cartService.addProductToUserCart(user.getId(), inCartProduct.getId(), 3);

        CategoryPageData pageData = categoryService.getCategoryPage(
                category.getTitle(), user.getId(), "name_asc", 0, 12);

        Map<Long, Integer> quantities = pageData.getCartProductQuantities();
        assertThat(quantities).containsEntry(inCartProduct.getId(), 3);

        List<ProductCardDto> cards = pageData.getProducts().getContent();
        ProductCardDto card = cards.stream()
                .filter(c -> c.getId().equals(inCartProduct.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(card.isInCart()).isTrue();
        assertThat(card.getCartQuantity()).isEqualTo(3);
    }

    @Test
    void totalElementsMatchesPageWithoutExtraCountQuery() {
        CategoryPageData pageData = categoryService.getCategoryPage(
                category.getTitle(), user.getId(), "name_asc", 0, 12);

        assertThat(pageData.getTotalElements())
                .isEqualTo(pageData.getProducts().getTotalElements())
                .isEqualTo(2);
    }

    @Test
    void anonymousVisitorGetsEmptyCartQuantities() {
        CategoryPageData pageData = categoryService.getCategoryPage(
                category.getTitle(), null, "name_asc", 0, 12);

        assertThat(pageData.getCartProductQuantities()).isEmpty();
        assertThat(pageData.getTotalElements()).isEqualTo(2);
    }

    private long countCarts() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM cart WHERE user_id = ?",
                new Object[]{user.getId()}, Long.class);
        return count == null ? 0 : count;
    }
}
