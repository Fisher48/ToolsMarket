package ru.fisher.ToolsMarket.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.fisher.ToolsMarket.PostgresTestConfig;
import ru.fisher.ToolsMarket.dto.CartDTO.CartItemDto;
import ru.fisher.ToolsMarket.exceptions.OrderFinalizedException;
import ru.fisher.ToolsMarket.exceptions.OrderNotFoundException;
import ru.fisher.ToolsMarket.models.Order;
import ru.fisher.ToolsMarket.models.OrderItem;
import ru.fisher.ToolsMarket.models.OrderStatus;
import ru.fisher.ToolsMarket.models.Product;
import ru.fisher.ToolsMarket.models.User;
import ru.fisher.ToolsMarket.models.UserType;
import ru.fisher.ToolsMarket.service.CartService;
import ru.fisher.ToolsMarket.service.OrderService;
import ru.fisher.ToolsMarket.service.UserService;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

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

    private void stubUser(Long id) {
        User user = User.builder()
                .id(id)
                .username("testuser")
                .email("test@example.com")
                .userType(UserType.REGULAR)
                .build();
        when(userService.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(userService.findById(id)).thenReturn(Optional.of(user));
    }

    private OrderItem buildOrderItem(Long id, Long productId, BigDecimal price, int quantity) {
        Product product = new Product();
        product.setId(productId);
        product.setName("Test Product");
        product.setTitle("Test Product");
        product.setImages(new LinkedHashSet<>());

        return OrderItem.builder()
                .id(id)
                .product(product)
                .productName("Test Product")
                .productSku("TEST-001")
                .quantity(quantity)
                .unitPrice(price)
                .subtotal(price.multiply(BigDecimal.valueOf(quantity)))
                .build();
    }

    private Order buildOrder(Long id, Long userId, OrderStatus status, Set<OrderItem> items) {
        Order order = Order.builder()
                .id(id)
                .orderNumber(12345L)
                .user(User.builder().id(userId).username("testuser").userType(UserType.REGULAR).build())
                .status(status)
                .totalPrice(BigDecimal.valueOf(3000))
                .orderItems(items)
                .build();
        if (items != null) {
            items.forEach(item -> item.setOrder(order));
        }
        return order;
    }

    @Test
    @WithMockUser(username = "testuser")
    void viewOrderReturnsOrderPage() throws Exception {
        Long orderId = 1L;
        stubUser(1L);

        Order order = buildOrder(orderId, 1L, OrderStatus.CREATED,
                Set.of(buildOrderItem(1L, 1L, BigDecimal.valueOf(1500), 2)));
        when(orderService.getOrderWithProducts(orderId)).thenReturn(order);

        mockMvc.perform(get("/order/{id}", orderId).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("order/index"))
                .andExpect(model().attribute("order", order))
                .andExpect(model().attribute("orderItems", hasSize(1)))
                .andExpect(model().attribute("canCancel", true))
                .andExpect(model().attribute("originalTotal", BigDecimal.valueOf(3000)))
                .andExpect(model().attributeExists("currentUser", "isPublicView"));

        verify(orderService).getOrderWithProducts(orderId);
    }

    @Test
    @WithMockUser(username = "testuser")
    void viewOrderRedirectsToLoginWhenUserNotFound() throws Exception {
        when(userService.findByUsername("testuser")).thenReturn(Optional.empty());

        mockMvc.perform(get("/order/1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/auth/login"));
    }

    @Test
    void viewOrderAsGuestRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/order/1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/auth/login"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void viewNonExistentOrderReturns404() throws Exception {
        stubUser(1L);
        when(orderService.getOrderWithProducts(999L))
                .thenThrow(new OrderNotFoundException(999L));

        mockMvc.perform(get("/order/{id}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Заказ не найден"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path").value("/order/999"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void checkoutAuthenticatedUserWithEmptyCartShowsCheckoutPage() throws Exception {
        stubUser(1L);
        when(cartService.getUserCartItems(1L)).thenReturn(List.of());

        mockMvc.perform(get("/order/checkout").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("order/checkout"))
                .andExpect(model().attribute("items", hasSize(0)))
                .andExpect(model().attribute("totalAmount", BigDecimal.ZERO))
                .andExpect(model().attribute("hasDiscounts", false))
                .andExpect(model().attributeExists("currentUser"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void checkoutWithNonEmptyCartComputesTotals() throws Exception {
        stubUser(1L);

        CartItemDto item = new CartItemDto();
        item.setProductId(1L);
        item.setProductName("Тестовый товар");
        item.setQuantity(2);
        item.setUnitPrice(new BigDecimal("100.00"));
        item.setTotalPriceWithDiscount(new BigDecimal("180.00"));

        when(cartService.getUserCartItems(1L)).thenReturn(List.of(item));

        mockMvc.perform(get("/order/checkout").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("order/checkout"))
                .andExpect(model().attribute("items", hasSize(1)))
                .andExpect(model().attribute("totalAmount", new BigDecimal("200.00")))
                .andExpect(model().attribute("totalWithDiscount", new BigDecimal("180.00")))
                .andExpect(model().attribute("totalDiscount", new BigDecimal("20.00")))
                .andExpect(model().attribute("hasDiscounts", true));
    }

    @Test
    void checkoutAsGuestRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/order/checkout"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/auth/login"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void createOrderCreatesOrderAndRedirects() throws Exception {
        stubUser(1L);

        when(cartService.getUserCartItems(1L)).thenReturn(List.of(new CartItemDto()));
        Order order = new Order();
        order.setId(5L);
        order.setOrderNumber(12345L);
        when(orderService.createOrderFromUserCart(eq(1L), any())).thenReturn(order);

        mockMvc.perform(post("/order/create").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/order/5"))
                .andExpect(flash().attribute("successMessage",
                        "Заказ №12345 успешно создан!"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void createOrderWithEmptyCartRedirectsToCartWithError() throws Exception {
        stubUser(1L);
        when(cartService.getUserCartItems(1L)).thenReturn(List.of());

        mockMvc.perform(post("/order/create").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cart"))
                .andExpect(flash().attribute("errorMessage",
                        "Невозможно создать заказ из пустой корзины"));

        verify(orderService, never()).createOrderFromUserCart(any(), any());
    }

    @Test
    @WithMockUser(username = "testuser")
    void createOrderFromEmptyCartShowsErrorMessage() throws Exception {
        stubUser(1L);

        when(cartService.getUserCartItems(1L)).thenReturn(List.of(new CartItemDto()));
        when(orderService.createOrderFromUserCart(eq(1L), any()))
                .thenThrow(new IllegalStateException("Cart is empty"));

        mockMvc.perform(post("/order/create").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cart"))
                .andExpect(flash().attribute("errorMessage",
                        "Невозможно создать заказ из пустой корзины"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void createOrderWithServiceErrorShowsGenericMessage() throws Exception {
        stubUser(1L);

        when(cartService.getUserCartItems(1L)).thenReturn(List.of(new CartItemDto()));
        when(orderService.createOrderFromUserCart(eq(1L), any()))
                .thenThrow(new IllegalArgumentException("Cart not found"));

        mockMvc.perform(post("/order/create").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cart"))
                .andExpect(flash().attribute("errorMessage",
                        "Произошла ошибка при создании заказа"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void createOrderAsUserNotFoundRedirectsToLogin() throws Exception {
        when(userService.findByUsername("testuser")).thenReturn(Optional.empty());

        mockMvc.perform(post("/order/create").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/auth/login"));

        verify(orderService, never()).createOrderFromUserCart(any(), any());
    }

    @Test
    @WithMockUser(username = "testuser")
    void cancelOrderSuccessfully() throws Exception {
        Long orderId = 100L;
        stubUser(1L);

        Order order = buildOrder(orderId, 1L, OrderStatus.CREATED, Set.of());
        when(orderService.getUserOrder(orderId, 1L)).thenReturn(order);
        when(orderService.updateStatus(orderId, OrderStatus.CANCELLED)).thenReturn(order);

        mockMvc.perform(post("/order/{id}/cancel", orderId).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/order/" + orderId))
                .andExpect(flash().attribute("successMessage", "Заказ отменен"));

        verify(orderService).updateStatus(orderId, OrderStatus.CANCELLED);
    }

    @Test
    @WithMockUser(username = "testuser")
    void cancelNonExistentOrderShowsError() throws Exception {
        Long orderId = 999L;
        stubUser(1L);

        when(orderService.getUserOrder(orderId, 1L))
                .thenThrow(new OrderNotFoundException(orderId));

        mockMvc.perform(post("/order/{id}/cancel", orderId).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/order/" + orderId))
                .andExpect(flash().attribute("errorMessage", "Заказ не найден"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void cancelAlreadyFinalizedOrderShowsError() throws Exception {
        Long orderId = 100L;
        stubUser(1L);

        Order order = buildOrder(orderId, 1L, OrderStatus.CREATED, Set.of());
        when(orderService.getUserOrder(orderId, 1L)).thenReturn(order);
        doThrow(new OrderFinalizedException("COMPLETED"))
                .when(orderService).updateStatus(orderId, OrderStatus.CANCELLED);

        mockMvc.perform(post("/order/{id}/cancel", orderId).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/order/" + orderId))
                .andExpect(flash().attribute("errorMessage",
                        "Невозможно изменить статус заказа: заказ уже завершен"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void cancelOrderNotAllowedFromCurrentStatus() throws Exception {
        Long orderId = 100L;
        stubUser(1L);

        Order order = buildOrder(orderId, 1L, OrderStatus.COMPLETED, Set.of());
        when(orderService.getUserOrder(orderId, 1L)).thenReturn(order);

        mockMvc.perform(post("/order/{id}/cancel", orderId).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/order/" + orderId))
                .andExpect(flash().attribute("errorMessage",
                        "Вы не можете отменить этот заказ"));

        verify(orderService, never()).updateStatus(eq(orderId), any());
    }

    @Test
    void orderHistoryAsGuestRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/order/history"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/auth/login"));
    }
}
