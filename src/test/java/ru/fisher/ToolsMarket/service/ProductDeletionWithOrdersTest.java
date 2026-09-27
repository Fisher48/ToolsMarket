package ru.fisher.ToolsMarket.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import ru.fisher.ToolsMarket.PostgresTestConfig;
import ru.fisher.ToolsMarket.dto.OrderDTO.OrderItemDto;
import ru.fisher.ToolsMarket.models.Cart;
import ru.fisher.ToolsMarket.models.Order;
import ru.fisher.ToolsMarket.models.OrderItem;
import ru.fisher.ToolsMarket.models.Product;
import ru.fisher.ToolsMarket.models.User;
import ru.fisher.ToolsMarket.repository.CartItemRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Удаление товара, на который ссылаются заказы или корзины.
 *
 * order_item объявлял product_id NOT NULL, хотя FK был ON DELETE SET NULL —
 * противоречие, из-за которого удаление падало на 23502. cart_item вообще
 * ссылался на product без ON DELETE (NO ACTION) и ронял удаление так же.
 */
@SpringBootTest
@ContextConfiguration(initializers = PostgresTestConfig.class)
class ProductDeletionWithOrdersTest {

    @Autowired
    private OrderService orderService;
    @Autowired
    private ProductService productService;
    @Autowired
    private CartService cartService;
    @Autowired
    private UserService userService;
    @Autowired
    private CartItemRepository cartItemRepository;
    @Autowired
    private JdbcTemplate jdbc;

    private User testUser;

    @BeforeEach
    void setup() {
        testUser = User.builder()
                .username("deluser_" + UUID.randomUUID().toString().substring(0, 8))
                .email("del_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com")
                .password("password")
                .build();
        userService.createAdminUser(testUser.getUsername(), testUser.getEmail(), testUser.getPassword());
        testUser = userService.findByUsername(testUser.getUsername()).orElseThrow();
    }

    @AfterEach
    void cleanup() {
        jdbc.execute("TRUNCATE TABLE order_item RESTART IDENTITY CASCADE");
        jdbc.execute("TRUNCATE TABLE \"order\" RESTART IDENTITY CASCADE");
        jdbc.execute("TRUNCATE TABLE cart_item RESTART IDENTITY CASCADE");
        jdbc.execute("TRUNCATE TABLE cart RESTART IDENTITY CASCADE");
        jdbc.execute("TRUNCATE TABLE product RESTART IDENTITY CASCADE");
        jdbc.execute("TRUNCATE TABLE users RESTART IDENTITY CASCADE");
    }

    private Product createAndSaveProduct(String name, BigDecimal price) {
        Product product = Product.builder()
                .name(name)
                .images(new LinkedHashSet<>())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .price(price)
                .attributeValues(new LinkedHashSet<>())
                .active(true)
                .currency("RUB")
                .categories(new HashSet<>())
                .sku("SKU-" + name)
                .title("Title-" + name)
                .shortDescription("short-desc")
                .description("description")
                .build();
        productService.saveEntity(product);
        return product;
    }

    private Order placeOrderWith(Product product) {
        Cart cart = cartService.getOrCreateCart(testUser.getId());
        cartService.addProductWithQuantity(cart.getId(), product.getId(), 1);
        return orderService.createOrder(cart.getId(), "");
    }

    @Test
    void deletingProductReferencedByOrderKeepsOrderReadable() {
        // given: товар уже заказан
        Product product = createAndSaveProduct("ordered", new BigDecimal("1000.00"));
        Order order = placeOrderWith(product);

        // when: товар удаляют
        productService.deleteEntity(product.getId());

        // then: заказ остаётся, позиция читается по сохранённому snapshot
        Order reloaded = orderService.getOrderWithProducts(order.getId());
        assertThat(reloaded.getOrderItems()).hasSize(1);
        OrderItem item = reloaded.getOrderItems().iterator().next();
        assertThat(item.getProduct()).isNull();

        OrderItemDto dto = OrderItemDto.fromEntity(item);
        assertThat(dto.getProductName()).isEqualTo("ordered");
        assertThat(dto.getUnitPrice()).isEqualByComparingTo("1000.00");
        // блок скидок обязан отработать и без товара: иначе originalPrice null,
        // а на него завязаны суммы в профиле и в письме
        assertThat(dto.getOriginalPrice()).isEqualByComparingTo("1000.00");
        assertThat(dto.isHasDiscount()).isFalse();
    }

    @Test
    void deletingProductPresentInCartRemovesItFromCart() {
        // given: товар лежит в корзине, заказа ещё нет
        Product product = createAndSaveProduct("in-cart", new BigDecimal("500.00"));
        Cart cart = cartService.getOrCreateCart(testUser.getId());
        cartService.addProductWithQuantity(cart.getId(), product.getId(), 2);
        assertThat(cartItemRepository.findByCartId(cart.getId())).hasSize(1);

        // when
        productService.deleteEntity(product.getId());

        // then: позиция исчезла из корзины, а не сломала удаление
        assertThat(cartItemRepository.findByCartId(cart.getId())).isEmpty();
    }
}
