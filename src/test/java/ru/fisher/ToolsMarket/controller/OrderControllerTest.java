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
import ru.fisher.ToolsMarket.models.Cart;
import ru.fisher.ToolsMarket.models.Order;
import ru.fisher.ToolsMarket.models.OrderStatus;
import ru.fisher.ToolsMarket.service.CartService;
import ru.fisher.ToolsMarket.service.OrderService;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = PostgresTestConfig.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private CartService cartService;

    @Test
    @WithMockUser(username = "testuser")
    void viewOrderReturnsOrderPage() throws Exception {
        Order order = new Order();
        order.setId(1L);
        order.setOrderNumber(12345L);
        order.setStatus(OrderStatus.CREATED);
        order.setTotalPrice(BigDecimal.valueOf(3000));

        when(orderService.getOrder(1L)).thenReturn(order);

        mockMvc.perform(get("/order/1"))
                .andExpect(status().isOk())
                .andExpect(view().name("order/index"))
                .andExpect(model().attributeExists("order"))
                .andExpect(model().attribute("order", order));
    }

    @Test
    @WithMockUser(username = "testuser")
    void viewNonExistentOrderReturns404() throws Exception {
        Long nonExistentOrderId = 999L;

        when(orderService.getOrder(nonExistentOrderId))
                .thenThrow(new OrderNotFoundException(nonExistentOrderId));

        mockMvc.perform(get("/order/{id}", nonExistentOrderId))
                .andExpect(jsonPath("$.error").value("Заказ не найден"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path").value("/order/999"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void checkoutWithEmptyCartRedirectsToCart() throws Exception {
        String sessionId = "abc";

        Cart cart = new Cart();
        cart.setId(10L);

        when(cartService.getOrCreateCart(null, sessionId)).thenReturn(cart);
        when(cartService.getCartItems(10L)).thenReturn(List.of());

        mockMvc.perform(get("/order/checkout")
                        .cookie(new Cookie("sessionId", sessionId)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cart?error=empty"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void checkoutWithNonEmptyCartShowsCheckoutPage() throws Exception {
        String sessionId = "abc";

        Cart cart = new Cart();
        cart.setId(10L);

        // Создаем элементы корзины
        CartItemDto item = new CartItemDto();
        item.setProductId(1L);
        item.setProductName("Тестовый товар");
        item.setQuantity(1);
        item.setUnitPrice(new BigDecimal("100.00"));

        List<CartItemDto> items = List.of(item);

        when(cartService.getOrCreateCart(null, sessionId)).thenReturn(cart);
        when(cartService.getCartItems(10L)).thenReturn(items);

        mockMvc.perform(get("/order/checkout")
                        .cookie(new Cookie("sessionId", sessionId)))
                .andExpect(status().isOk())
                .andExpect(view().name("order/checkout"))
                .andExpect(model().attribute("cart", cart))
                .andExpect(model().attribute("items", items));
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
    void checkoutWithoutSessionCookieReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/order/checkout"))
                .andExpect(status().isBadRequest());
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
        mockMvc.perform(post("/admin/orders/1/status")
                        .param("status", "PAID"))
                .andExpect(status().isForbidden());
    }

}
