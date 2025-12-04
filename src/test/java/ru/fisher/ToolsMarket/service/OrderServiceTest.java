package ru.fisher.ToolsMarket.service;


import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import ru.fisher.ToolsMarket.PostgresTestConfig;
import ru.fisher.ToolsMarket.exceptions.InvalidStatusTransitionException;
import ru.fisher.ToolsMarket.exceptions.OrderFinalizedException;
import ru.fisher.ToolsMarket.exceptions.OrderNotFoundException;
import ru.fisher.ToolsMarket.exceptions.OrderValidationException;
import ru.fisher.ToolsMarket.models.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ContextConfiguration(initializers = PostgresTestConfig.class)
class OrderServiceTest {

    @Autowired
    private OrderService orderService;
    @Autowired
    private ProductService productService;
    @Autowired
    private CartService cartService;
    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void cleanup() {
        jdbc.execute("TRUNCATE TABLE order_item RESTART IDENTITY CASCADE");
        jdbc.execute("TRUNCATE TABLE \"order\" RESTART IDENTITY CASCADE");
        jdbc.execute("TRUNCATE TABLE cart_item RESTART IDENTITY CASCADE");
        jdbc.execute("TRUNCATE TABLE cart RESTART IDENTITY CASCADE");
        jdbc.execute("TRUNCATE TABLE product RESTART IDENTITY CASCADE");
    }

    public Product createAndSaveProduct(String productName, BigDecimal price) {
        Product product = Product.builder()
                .name(productName)
                .images(new ArrayList<>())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .price(price)
                .attributeValues(new ArrayList<>())
                .active(true)
                .currency("RUB")
                .categories(new HashSet<>())
                .sku("SKU-" + productName)
                .title("Title-" + productName)
                .shortDescription("short-desc")
                .description("description")
                .build();
        productService.saveEntity(product);
        return product;
    }

    @Test
    void createOrderFromCartCopiesItemsAndClearCartTest() {
        // Given - Создаем корзину с товарами
        Cart cart = cartService.getOrCreateCart(null, UUID.randomUUID().toString());

        Product p1 = createAndSaveProduct("p1", BigDecimal.valueOf(1000.0));
        Product p2 = createAndSaveProduct("p2", BigDecimal.valueOf(2000.0));

        cartService.addProductWithQuantity(cart.getId(), p1.getId(), 2);
        cartService.addProductWithQuantity(cart.getId(), p2.getId(), 1);

        // When - Создаем заказ из корзины
        Order order = orderService.createOrder(cart.getId());


        // Then
        assertThat(order.getId()).isNotNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(order.getTotalPrice())
                .isEqualByComparingTo(p1.getPrice()
                        .multiply(BigDecimal.valueOf(2))
                                .add(p2.getPrice()));
        assertEquals(2, order.getOrderItems().size());

        OrderItem item1 = find(order.getOrderItems(), p1.getId());
        assertThat(item1.getProductId()).isEqualTo(p1.getId());
        assertThat(item1.getProductName()).isEqualTo("p1");
        assertThat(item1.getProductSku()).isEqualTo(p1.getSku());
        assertThat(item1.getQuantity()).isEqualTo(2);
        assertThat(item1.getSubtotal())
                .isEqualByComparingTo(p1.getPrice()
                        .multiply(BigDecimal.valueOf(2)));

        // Корзина очищена
        assertThat(cartService.getCartItems(cart.getId()).isEmpty());
    }

    private OrderItem find(List<OrderItem> items, Long productId) {
        return items.stream()
                .filter(i -> i.getProductId().equals(productId))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void getOrderByIdReturnsOrderWithItems() {
        Cart cart = cartService.getOrCreateCart(null, UUID.randomUUID().toString());

        Product p1 = createAndSaveProduct("p1", BigDecimal.valueOf(1000.0));
        Product p2 = createAndSaveProduct("p2", BigDecimal.valueOf(2000.0));

        cartService.addProductWithQuantity(cart.getId(), p1.getId(), 2);
        cartService.addProductWithQuantity(cart.getId(), p2.getId(), 1);

        Order order = orderService.createOrder(cart.getId());

        Order fromDb = orderService.getOrder(order.getId());

        assertThat(fromDb.getId()).isEqualTo(order.getId());
        assertEquals(2, fromDb.getOrderItems().size());
    }

    @Test
    void productChangesDoNotAffectExistingOrders() {
        Product p1 = createAndSaveProduct("Test", BigDecimal.valueOf(1000.0));
        Cart cart = cartService.getOrCreateCart(null, UUID.randomUUID().toString());
        cartService.addProductWithQuantity(cart.getId(), p1.getId(), 2);
        Order order = orderService.createOrder(cart.getId());

        // Меняем продукт
        p1.setName("CHANGED!");
        productService.saveEntity(p1);

        OrderItem item = order.getOrderItems().getFirst();
        assertThat(item.getProductName()).isEqualTo("Test");
    }

    @Test
    void updateOrderStatusAfterConfirmationTest() {
        Cart cart = cartService.getOrCreateCart(null, UUID.randomUUID().toString());

        Product p1 = createAndSaveProduct("p1", BigDecimal.valueOf(1000.0));

        cartService.addProductWithQuantity(cart.getId(), p1.getId(), 2);

        Order order = orderService.createOrder(cart.getId());
        assertThat(order.getId()).isNotNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);

        OrderItem orderItem1 = find(order.getOrderItems(), p1.getId());

        assertThat(orderItem1.getProductId()).isEqualTo(p1.getId());
        assertThat(orderItem1.getProductName()).isEqualTo("p1");
        assertThat(orderItem1.getProductSku()).isEqualTo(p1.getSku());
        assertThat(orderItem1.getQuantity()).isEqualTo(2);
        assertThat(orderItem1.getSubtotal());

        orderService.updateStatus(order.getId(), OrderStatus.PAID);

        // then
        Order updated = orderService.getOrder(order.getId());

        assertThat(updated.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(updated.getUpdatedAt()).isAfter(order.getUpdatedAt());

    }

    @Test
    void updateOrderStatusCannotGoBackwards() {
        Cart cart = cartService.getOrCreateCart(null, UUID.randomUUID().toString());
        Product p = createAndSaveProduct("p1", BigDecimal.valueOf(1000));
        cartService.addProductWithQuantity(cart.getId(), p.getId(), 1);
        Order order = orderService.createOrder(cart.getId());

        // Переводим вперед
        orderService.updateStatus(order.getId(), OrderStatus.PAID);

        // Попытка отката — ошибка
        assertThatThrownBy(() ->
                orderService.updateStatus(order.getId(), OrderStatus.CREATED)
        )
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("Некорректный переход статуса: PAID → CREATED");
    }

    @Test
    void updateOrderStatusCannotBeChangedAfterCompletion() {
        Cart cart = cartService.getOrCreateCart(null, UUID.randomUUID().toString());
        Product p = createAndSaveProduct("p1", BigDecimal.valueOf(1000));
        cartService.addProductWithQuantity(cart.getId(), p.getId(), 1);
        Order order = orderService.createOrder(cart.getId());

        orderService.updateStatus(order.getId(), OrderStatus.COMPLETED);

        assertThatThrownBy(() ->
                orderService.updateStatus(order.getId(), OrderStatus.PAID)
        )
                .isInstanceOf(OrderFinalizedException.class)
                .hasMessageContaining("Невозможно изменить статус заказа: заказ уже завершен");
    }

    @Test
    void updateOrderStatusThrowsWhenOrderNotFound() {
        assertThatThrownBy(() ->
                orderService.updateStatus(999L, OrderStatus.PAID)
        )
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining("Заказ с ID 999 не найден");
    }

    @Test
    void updateOrderStatusThrowsOnNullStatus() {
        Cart cart = cartService.getOrCreateCart(null, UUID.randomUUID().toString());
        Product p = createAndSaveProduct("p", BigDecimal.valueOf(1000));
        cartService.addProductWithQuantity(cart.getId(), p.getId(), 1);
        Order order = orderService.createOrder(cart.getId());

        assertThatThrownBy(() ->
                orderService.updateStatus(order.getId(), null)
        )
                .isInstanceOf(OrderValidationException.class)
                .hasMessageContaining("Ошибка валидации поля 'status': Статус не может быть null");
    }

    @Test
    void createOrderFromEmptyCartThrowsException() {
        // Given - пустая корзина
        Cart cart = cartService.getOrCreateCart(null, UUID.randomUUID().toString());

        // When & Then
        assertThatThrownBy(() -> orderService.createOrder(cart.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cart is empty");
    }

    @Test
    void createOrderFromNonExistentCartThrowsException() {
        // Given - несуществующий ID корзины
        Long nonExistentCartId = 999L;

        // When & Then
        assertThatThrownBy(() -> orderService.createOrder(nonExistentCartId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cart not found");
    }

    @Test
    void orderNumberIsUniqueAndGenerated() {
        // Given - корзина с товаром
        Cart cart = cartService.getOrCreateCart(null, UUID.randomUUID().toString());
        Product product = createAndSaveProduct("Test", BigDecimal.valueOf(1000));
        cartService.addProduct(cart.getId(), product.getId());

        // When - создаем два заказа подряд
        Order order1 = orderService.createOrder(cart.getId());

        // Новая корзина для второго заказа
        Cart cart2 = cartService.getOrCreateCart(null, UUID.randomUUID().toString());
        cartService.addProduct(cart2.getId(), product.getId());
        Order order2 = orderService.createOrder(cart2.getId());

        // Then - номера заказов уникальны
        assertThat(order1.getOrderNumber()).isNotEqualTo(order2.getOrderNumber());
        assertThat(order1.getOrderNumber()).isNotNull();
        assertThat(order2.getOrderNumber()).isNotNull();
    }


}
