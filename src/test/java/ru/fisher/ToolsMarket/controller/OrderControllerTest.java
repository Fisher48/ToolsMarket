package ru.fisher.ToolsMarket.controller;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.fisher.ToolsMarket.PostgresTestConfig;
import ru.fisher.ToolsMarket.dto.CartItemDto;
import ru.fisher.ToolsMarket.exceptions.OrderFinalizedException;
import ru.fisher.ToolsMarket.exceptions.OrderNotFoundException;
import ru.fisher.ToolsMarket.models.*;
import ru.fisher.ToolsMarket.service.CartService;
import ru.fisher.ToolsMarket.service.OrderService;
import ru.fisher.ToolsMarket.service.UserService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(initializers = PostgresTestConfig.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private CartService cartService;

    @MockitoBean
    private UserService userService;

    @Test
    @WithMockUser(username = "testuser")
    void viewOrderReturnsOrderPage() throws Exception {
        Long orderId = 1L;
        Long userId = 1L; // ID пользователя testuser (предполагаемый)

        // 1. Создаем пользователя
        User user = User.builder()
                .id(userId)
                .username("testuser")
                .email("test@example.com")
                .build();

        // 2. Создаем OrderItem
        OrderItem orderItem = OrderItem.builder()
                .id(1L)
                .product(new Product())
                .productName("Test Product")
                .productSku("TEST-001")
                .quantity(2)
                .unitPrice(BigDecimal.valueOf(1500))
                .subtotal(BigDecimal.valueOf(3000))
                .build();

        // 3. Создаем Order с пользователем
        Order order = Order.builder()
                .id(orderId)
                .orderNumber(12345L)
                .user(user) // ← ВАЖНО: устанавливаем пользователя!
                .status(OrderStatus.CREATED)
                .totalPrice(BigDecimal.valueOf(3000))
                .orderItems(Set.of(orderItem))
                .build();

        // 4. Устанавливаем связь OrderItem -> Order
        orderItem.setOrder(order);

        // 5. Мокаем метод: getOrderWithProducts()
        when(orderService.getOrderWithProducts(orderId))
                .thenReturn(order);

        // 6. Если есть зависимость от userService в getCurrentUserId()
        when(userService.findByUsername("testuser"))
                .thenReturn(Optional.of(user));

        mockMvc.perform(get("/order/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(view().name("order/index"))
                .andExpect(model().attributeExists("order"))
                .andExpect(model().attribute("order", order))
                .andExpect(model().attributeExists("orderItems"))
                .andExpect(model().attribute("canCancel", true));
    }

    @Test
    @WithMockUser(username = "testuser")
    void viewNonExistentOrderReturns404() throws Exception {
        Long nonExistentOrderId = 999L;

        when(orderService.getOrderWithProducts(nonExistentOrderId))
                .thenThrow(new OrderNotFoundException(nonExistentOrderId));

        mockMvc.perform(get("/order/{id}", nonExistentOrderId).with(csrf()))
                .andExpect(jsonPath("$.error").value("Заказ не найден"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path").value("/order/999"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void checkout_AuthenticatedUserWithEmptyCart_ShowsEmptyCheckout() throws Exception {
        // Arrange
        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        user.setUserType(UserType.REGULAR);

        when(userService.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(userService.findById(1L)).thenReturn(Optional.of(user));
        when(cartService.getUserCartItems(1L)).thenReturn(List.of());

        // Act & Assert
        mockMvc.perform(get("/order/checkout").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("order/checkout"))
                .andExpect(model().attribute("items", hasSize(0)))
                .andExpect(model().attribute("totalAmount", BigDecimal.ZERO))
                .andExpect(model().attribute("isAuthenticated", true));
    }

    @Test
    void checkoutWithNonEmptyCartShowsCheckoutPage_Unauthenticated() throws Exception {
        // 1. Создаем корзину
        Cart cart = new Cart();
        cart.setId(10L);

        // 2. Создаем товары в корзине
        CartItemDto item = new CartItemDto();
        item.setProductId(1L);
        item.setProductName("Тестовый товар");
        item.setQuantity(2); // 2 штуки
        item.setUnitPrice(new BigDecimal("100.00"));
        item.setTotalPriceWithDiscount(new BigDecimal("180.00")); // Со скидкой 10%

        List<CartItemDto> items = List.of(item);

        // 3. Мокаем сервисы (для НЕАВТОРИЗОВАННОГО пользователя)
        when(cartService.getOrCreateCart(null, null)).thenReturn(cart); // ← sessionId не используется!
        when(cartService.getCartItems(10L)).thenReturn(items);

        // 4. Выполняем запрос БЕЗ авторизации
        mockMvc.perform(get("/order/checkout"))
                .andExpect(status().isOk())
                .andExpect(view().name("order/checkout"))
                .andExpect(model().attributeExists("items"))
                .andExpect(model().attribute("items", items))
                .andExpect(model().attribute("totalAmount", new BigDecimal("200.00"))) // 2 * 100
                .andExpect(model().attribute("totalWithDiscount", new BigDecimal("180.00")))
                .andExpect(model().attribute("totalDiscount", new BigDecimal("20.00")))
                .andExpect(model().attribute("isAuthenticated", false));
    }

    @Test
    @WithMockUser(username = "testuser")
    void createOrderCreatesOrderAndRedirects() throws Exception {
        String sessionId = "abc";

        Cart cart = new Cart();
        cart.setId(10L);

        Order order = new Order();
        order.setId(5L);
        order.setOrderNumber(12345L);

        when(cartService.getOrCreateCart(null, sessionId)).thenReturn(cart);
        when(cartService.getCartItems(10L)).thenReturn(List.of(new CartItemDto()));
        when(orderService.createOrder(10L)).thenReturn(order);

        mockMvc.perform(post("/order/create")
                        .cookie(new Cookie("sessionId", sessionId)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/order/5"))
                .andExpect(flash().attribute("successMessage",
                        "Заказ №12345 успешно создан!"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void createOrderWithEmptyCartRedirectsToCartWithError() throws Exception {
        String sessionId = "abc";

        Cart cart = new Cart();
        cart.setId(10L);

        when(cartService.getOrCreateCart(null, sessionId)).thenReturn(cart);
        when(cartService.getCartItems(10L)).thenReturn(List.of()); // Пустая корзина

        mockMvc.perform(post("/order/create")
                        .cookie(new Cookie("sessionId", sessionId)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cart"))
                .andExpect(flash().attribute("errorMessage",
                        "Невозможно создать заказ из пустой корзины"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void createOrderFromEmptyCartShowsErrorMessage() throws Exception {
        String sessionId = "test-session";
        Long cartId = 1L;

        Cart cart = new Cart();
        cart.setId(cartId);

        when(cartService.getOrCreateCart(null, sessionId)).thenReturn(cart);
        when(orderService.createOrder(cartId))
                .thenThrow(new IllegalStateException("Cart is empty"));

        mockMvc.perform(post("/order/create")
                        .cookie(new Cookie("sessionId", sessionId)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cart"))
                .andExpect(flash().attribute("errorMessage",
                        "Невозможно создать заказ из пустой корзины"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void createOrderWithNotFoundCartShowsErrorMessage() throws Exception {
        String sessionId = "test-session";

        when(cartService.getOrCreateCart(null, sessionId))
                .thenThrow(new IllegalArgumentException("Cart not found"));

        mockMvc.perform(post("/order/create")
                        .cookie(new Cookie("sessionId", sessionId)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cart"))
                .andExpect(flash().attribute("errorMessage",
                        "Корзина не найдена"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void cancelOrderSuccessfully() throws Exception {
        Long orderId = 100L;
        Order cancelledOrder = new Order();
        cancelledOrder.setId(orderId);
        cancelledOrder.setOrderNumber(123456L);
        cancelledOrder.setStatus(OrderStatus.CANCELLED);

        // Исправляем: когда метод возвращает значение, используем thenReturn
        when(orderService.updateStatus(orderId, OrderStatus.CANCELLED))
                .thenReturn(cancelledOrder);

        mockMvc.perform(post("/order/{id}/cancel", orderId))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/order/" + orderId))
                .andExpect(flash().attribute("successMessage",
                        "Заказ отменен"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void cancelNonExistentOrderShowsError() throws Exception {
        Long orderId = 999L;

        doThrow(new OrderNotFoundException(orderId))
                .when(orderService).updateStatus(orderId, OrderStatus.CANCELLED);

        mockMvc.perform(post("/order/{id}/cancel", orderId))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/order/" + orderId))
                .andExpect(flash().attribute("errorMessage",
                        "Заказ не найден"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void cancelAlreadyCompletedOrderShowsError() throws Exception {
        Long orderId = 100L;

        doThrow(new OrderFinalizedException("COMPLETED"))
                .when(orderService).updateStatus(orderId, OrderStatus.CANCELLED);

        mockMvc.perform(post("/order/{id}/cancel", orderId))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/order/" + orderId))
                .andExpect(flash().attribute("errorMessage",
                        "Невозможно изменить статус заказа: заказ уже завершен"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void createOrderWithoutSessionCookieReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/order/create"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "testuser")
    void userCannotUpdateStatusThroughAdminEndpoint() throws Exception {
        // Даже если попробовать обратиться к админскому эндпоинту
        // (должна быть проверка авторизации в AdminOrderController)
        mockMvc.perform(post("/admin/orders/1/status").with(csrf())
                        .param("status", "PAID"))
                .andExpect(status().is3xxRedirection());
    }

}
