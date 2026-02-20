package ru.fisher.ToolsMarket.service;


import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.ContextConfiguration;
import ru.fisher.ToolsMarket.PostgresTestConfig;
import ru.fisher.ToolsMarket.exceptions.InvalidStatusTransitionException;
import ru.fisher.ToolsMarket.exceptions.OrderFinalizedException;
import ru.fisher.ToolsMarket.exceptions.OrderNotFoundException;
import ru.fisher.ToolsMarket.exceptions.OrderValidationException;
import ru.fisher.ToolsMarket.models.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

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
    private UserService userService;
    private User testUser;
    private Authentication originalAuthentication;
    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void cleanup() {
        // Восстанавливаем оригинальный контекст безопасности
        SecurityContextHolder.getContext().setAuthentication(originalAuthentication);

        // Очищаем базу данных
        jdbc.execute("TRUNCATE TABLE order_item RESTART IDENTITY CASCADE");
        jdbc.execute("TRUNCATE TABLE \"order\" RESTART IDENTITY CASCADE");
        jdbc.execute("TRUNCATE TABLE cart_item RESTART IDENTITY CASCADE");
        jdbc.execute("TRUNCATE TABLE cart RESTART IDENTITY CASCADE");
        jdbc.execute("TRUNCATE TABLE product RESTART IDENTITY CASCADE");
        jdbc.execute("TRUNCATE TABLE users RESTART IDENTITY CASCADE");
    }

    @BeforeEach
    void setup() {
        // Сохраняем оригинальный контекст безопасности
        originalAuthentication = SecurityContextHolder.getContext().getAuthentication();

        // Создаем тестового пользователя
        testUser = ru.fisher.ToolsMarket.models.User.builder()
                .username("testuser_" + UUID.randomUUID().toString().substring(0, 8))
                .email("test_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com")
                .password("password")
                .build();

        // Сохраняем пользователя в базу
        userService.createAdminUser(testUser.getUsername(), testUser.getEmail(), testUser.getPassword());

        // Получаем сохраненного пользователя из базы
        testUser = userService.findByUsername(testUser.getUsername()).orElseThrow();

        // Устанавливаем аутентификацию
        UserDetails userDetails = User.builder()
                .username(testUser.getUsername())
                .password("password")
                .roles(new HashSet<>())
                .build();

        Authentication auth = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    public Product createAndSaveProduct(String productName, BigDecimal price) {
        Product product = Product.builder()
                .name(productName)
                .images(new LinkedHashSet<>())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .price(price)
                .attributeValues(new LinkedHashSet<>())
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
        Cart cart = cartService.getOrCreateCart(testUser.getId());

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

        // Проверяем, что заказ связан с пользователем
        assertThat(order.getUser()).isNotNull();
        assertThat(order.getUser().getId()).isEqualTo(testUser.getId());

        OrderItem item1 = find(order.getOrderItems().stream().toList(), p1.getId());
        assertThat(item1.getProduct().getId()).isEqualTo(p1.getId());
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
                .filter(i -> i.getProduct().getId().equals(productId))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void getOrderByIdReturnsOrderWithItems() {
        Cart cart = cartService.getOrCreateCart(testUser.getId());

        Product p1 = createAndSaveProduct("p1", BigDecimal.valueOf(1000.0));
        Product p2 = createAndSaveProduct("p2", BigDecimal.valueOf(2000.0));

        cartService.addProductWithQuantity(cart.getId(), p1.getId(), 2);
        cartService.addProductWithQuantity(cart.getId(), p2.getId(), 1);

        Order order = orderService.createOrder(cart.getId());

        Order fromDb = orderService.getOrder(order.getId());

        assertThat(fromDb.getId()).isEqualTo(order.getId());
        assertEquals(2, fromDb.getOrderItems().size());
        assertThat(fromDb.getUser()).isNotNull();
        assertThat(fromDb.getUser().getId()).isEqualTo(testUser.getId());
    }

    @Test
    void getUserOrderReturnsOrderOnlyForCorrectUser() {
        Cart cart = cartService.getOrCreateCart(testUser.getId());
        Product p1 = createAndSaveProduct("p1", BigDecimal.valueOf(1000.0));
        cartService.addProductWithQuantity(cart.getId(), p1.getId(), 1);
        Order order = orderService.createOrder(cart.getId());

        // Должен вернуть заказ для правильного пользователя
        Order userOrder = orderService.getUserOrder(order.getId(), testUser.getId());
        assertThat(userOrder.getId()).isEqualTo(order.getId());

        // Должен бросить исключение для другого пользователя
        Long wrongUserId = testUser.getId() + 999L;
        assertThatThrownBy(() -> orderService.getUserOrder(order.getId(), wrongUserId))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining("не найден"); // Проверяем часть сообщения
    }

    @Test
    void getUserOrdersReturnsOnlyUserOrders() {
        // Создаем заказ для тестового пользователя
        Cart cart1 = cartService.getOrCreateCart(testUser.getId());
        Product p1 = createAndSaveProduct("p1", BigDecimal.valueOf(1000.0));
        cartService.addProduct(cart1.getId(), p1.getId());
        Order order1 = orderService.createOrder(cart1.getId());

        // Создаем второго пользователя и его заказ
        ru.fisher.ToolsMarket.models.User anotherUser = ru.fisher.ToolsMarket.models.User.builder()
                .username("another_" + UUID.randomUUID().toString().substring(0, 8))
                .email("another@example.com")
                .password("password")
                .build();
        userService.createAdminUser(anotherUser.getUsername(), anotherUser.getEmail(), anotherUser.getPassword());
        anotherUser = userService.findByUsername(anotherUser.getUsername()).orElseThrow();

        Cart cart2 = cartService.getOrCreateCart(anotherUser.getId());
        cartService.addProduct(cart2.getId(), p1.getId());
        Order order2 = orderService.createOrder(cart2.getId());

        // Получаем заказы только для тестового пользователя
        List<Order> userOrders = orderService.getUserOrders(testUser.getId());

        // Проверяем, что возвращен только заказ тестового пользователя
        assertThat(userOrders.size()).isEqualTo(1);
        assertThat(userOrders.get(0).getId()).isEqualTo(order1.getId());
        assertThat(userOrders.get(0).getUser().getId()).isEqualTo(testUser.getId());
    }

    @Test
    void getUserOrdersByStatusReturnsFilteredOrders() {
        Cart cart1 = cartService.getOrCreateCart(testUser.getId());
        Product p1 = createAndSaveProduct("p1", BigDecimal.valueOf(1000.0));
        cartService.addProduct(cart1.getId(), p1.getId());
        Order order1 = orderService.createOrder(cart1.getId());

        Cart cart2 = cartService.getOrCreateCart(testUser.getId());
        cartService.addProduct(cart2.getId(), p1.getId());
        Order order2 = orderService.createOrder(cart2.getId());

        // Меняем статус второго заказа
        orderService.updateStatus(order2.getId(), OrderStatus.PAID);

        // Получаем заказы по статусу CREATED
        List<Order> createdOrders = orderService.getUserOrdersByStatus(testUser.getId(), OrderStatus.CREATED);
        assertThat(createdOrders.size()).isEqualTo(1);
        assertThat(createdOrders.get(0).getId()).isEqualTo(order1.getId());

        // Получаем заказы по статусу PAID
        List<Order> paidOrders = orderService.getUserOrdersByStatus(testUser.getId(), OrderStatus.PAID);
        assertThat(paidOrders.size()).isEqualTo(1);
        assertThat(paidOrders.get(0).getId()).isEqualTo(order2.getId());
    }

    @Test
    void cancelOrderChangesStatusAndCanBeCancelledByUser() {
        Cart cart = cartService.getOrCreateCart(testUser.getId());
        Product p1 = createAndSaveProduct("p1", BigDecimal.valueOf(1000.0));
        cartService.addProduct(cart.getId(), p1.getId());
        Order order = orderService.createOrder(cart.getId());

        // Отменяем заказ
        orderService.cancelOrder(order.getId(), testUser.getId());

        Order cancelled = orderService.getOrder(order.getId());
        assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);

        // Проверяем, что нельзя отменить заказ другого пользователя
        Long wrongUserId = testUser.getId() + 999L;
        assertThatThrownBy(() -> orderService.cancelOrder(order.getId(), wrongUserId))
                .isInstanceOf(OrderNotFoundException.class) // Теперь OrderNotFoundException
                .hasMessageContaining("не найден");
    }

    @Test
    void cancelOrderOnlyAllowedForSpecificStatuses() {
        Cart cart = cartService.getOrCreateCart(testUser.getId());
        Product p1 = createAndSaveProduct("p1", BigDecimal.valueOf(1000.0));
        cartService.addProduct(cart.getId(), p1.getId());
        Order order = orderService.createOrder(cart.getId());

        // Меняем статус на COMPLETED
        orderService.updateStatus(order.getId(), OrderStatus.COMPLETED);

        // Проверяем, что нельзя отменить завершенный заказ
        assertThatThrownBy(() -> orderService.cancelOrder(order.getId(), testUser.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Невозможно отменить заказ в текущем статусе");
    }

    @Test
    void productChangesDoNotAffectExistingOrders() {
        Product p1 = createAndSaveProduct("Test", BigDecimal.valueOf(1000.0));
        Cart cart = cartService.getOrCreateCart(testUser.getId());
        cartService.addProductWithQuantity(cart.getId(), p1.getId(), 2);
        Order order = orderService.createOrder(cart.getId());

        // Меняем продукт
        p1.setName("CHANGED!");
        productService.saveEntity(p1);

        OrderItem item = order.getOrderItems().stream().toList().getFirst();
        assertThat(item.getProductName()).isEqualTo("Test");
    }

    @Test
    void updateOrderStatusAfterConfirmationTest() {
        Cart cart = cartService.getOrCreateCart(testUser.getId());

        Product p1 = createAndSaveProduct("p1", BigDecimal.valueOf(1000.0));

        cartService.addProductWithQuantity(cart.getId(), p1.getId(), 2);

        Order order = orderService.createOrder(cart.getId());
        assertThat(order.getId()).isNotNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(order.getUser().getId()).isEqualTo(testUser.getId());

        OrderItem orderItem1 = find(order.getOrderItems().stream().toList(), p1.getId());

        assertThat(orderItem1.getProduct().getId()).isEqualTo(p1.getId());
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
        Cart cart = cartService.getOrCreateCart(testUser.getId());
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
        Cart cart = cartService.getOrCreateCart(testUser.getId());
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
        Cart cart = cartService.getOrCreateCart(testUser.getId());
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
        Cart cart = cartService.getOrCreateCart(testUser.getId());

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
        Cart cart = cartService.getOrCreateCart(testUser.getId());
        Product product = createAndSaveProduct("Test", BigDecimal.valueOf(1000));
        cartService.addProduct(cart.getId(), product.getId());

        // When - создаем два заказа подряд
        Order order1 = orderService.createOrder(cart.getId());

        // Новая корзина для второго заказа
        Cart cart2 = cartService.getOrCreateCart(testUser.getId());
        cartService.addProduct(cart2.getId(), product.getId());
        Order order2 = orderService.createOrder(cart2.getId());

        // Then - номера заказов уникальны
        assertThat(order1.getOrderNumber()).isNotEqualTo(order2.getOrderNumber());
        assertThat(order1.getOrderNumber()).isNotNull();
        assertThat(order2.getOrderNumber()).isNotNull();
        assertThat(order1.getUser().getId()).isEqualTo(testUser.getId());
        assertThat(order2.getUser().getId()).isEqualTo(testUser.getId());
    }


}
