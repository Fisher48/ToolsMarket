package ru.fisher.ToolsMarket.controller;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.fisher.ToolsMarket.PostgresTestConfig;
import ru.fisher.ToolsMarket.exceptions.InvalidStatusTransitionException;
import ru.fisher.ToolsMarket.exceptions.OrderFinalizedException;
import ru.fisher.ToolsMarket.exceptions.OrderNotFoundException;
import ru.fisher.ToolsMarket.exceptions.OrderValidationException;
import ru.fisher.ToolsMarket.models.*;
import ru.fisher.ToolsMarket.service.OrderService;
import ru.fisher.ToolsMarket.service.UserService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.is;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(initializers = PostgresTestConfig.class)
class OrderAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private UserService userService;

    // =========== Тесты для списка заказов ===========

    @Test
    @WithMockUser(username = "testuser", roles = "ADMIN")
    void adminOrderListReturnsAllOrders() throws Exception {
        List<Order> orders = List.of(
                createOrder(1L, "CREATED"),
                createOrder(2L, "PAID")
        );

        when(orderService.getAllOrders()).thenReturn(orders);
        when(orderService.countOrdersByStatus(OrderStatus.CREATED)).thenReturn(1L);
        when(orderService.countOrdersByStatus(OrderStatus.PAID)).thenReturn(1L);
        when(orderService.countOrdersByStatus(OrderStatus.COMPLETED)).thenReturn(0L);
        when(orderService.countOrdersByStatus(OrderStatus.CANCELLED)).thenReturn(0L);

        mockMvc.perform(get("/admin/orders").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/orders/index"))
                .andExpect(model().attributeExists("orders", "newOrdersCount", "paidOrdersCount"))
                .andExpect(model().attribute("orders", orders))
                .andExpect(model().attribute("newOrdersCount", 1L))
                .andExpect(model().attribute("paidOrdersCount", 1L));
    }

    @Test
    @WithMockUser(username = "testuser", roles = "ADMIN")
    void adminOrderListWithStatusFilterReturnsFilteredOrders() throws Exception {
        List<Order> paidOrders = List.of(createOrder(2L, "PAID"));

        when(orderService.getOrdersByStatus(OrderStatus.PAID)).thenReturn(paidOrders);
        when(orderService.countOrdersByStatus(any())).thenReturn(0L);

        mockMvc.perform(get("/admin/orders").with(csrf())
                        .param("status", "PAID"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/orders/index"))
                .andExpect(model().attributeExists("orders"))
                .andExpect(model().attribute("orders", paidOrders));
    }

    @Test
    @WithMockUser(username = "testuser", roles = "ADMIN")
    void adminOrderListWithSearchByOrderNumber() throws Exception {
        Long orderNumber = 123456L;
        Order foundOrder = createOrder(1L, "CREATED");
        foundOrder.setOrderNumber(orderNumber);

        when(orderService.findByOrderNumber(orderNumber)).thenReturn(foundOrder);
        when(orderService.countOrdersByStatus(any())).thenReturn(0L);

        mockMvc.perform(get("/admin/orders").with(csrf())
                        .param("search", orderNumber.toString()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("orders", List.of(foundOrder)));
    }

    @Test
    @WithMockUser(username = "testuser", roles = "ADMIN")
    void adminOrderListWithSearchByProductSku() throws Exception {
        String sku = "TOOL-123";
        List<Order> foundOrders = List.of(createOrder(1L, "CREATED"));

        when(orderService.searchOrders(sku)).thenReturn(foundOrders);
        when(orderService.countOrdersByStatus(any())).thenReturn(0L);

        mockMvc.perform(get("/admin/orders").with(csrf())
                        .param("search", sku))
                .andExpect(status().isOk())
                .andExpect(model().attribute("orders", foundOrders));
    }

    @Test
    @WithMockUser(username = "testuser", roles = "ADMIN")
    void adminOrderListEmptyReturnsEmptyList() throws Exception {
        when(orderService.getAllOrders()).thenReturn(List.of());
        when(orderService.countOrdersByStatus(any(OrderStatus.class))).thenReturn(0L);

        mockMvc.perform(get("/admin/orders").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("orders", is(Matchers.empty())));
    }

    // =========== Тесты для деталей заказа ===========

    @Test
    @WithMockUser(username = "testuser", roles = "ADMIN")
    void adminOrderDetailReturnsOrderWithItems() throws Exception {
        Long orderId = 1L;
        Order order = createOrder(orderId, "CREATED");

        when(orderService.getOrderWithProducts(orderId)).thenReturn(order);

        mockMvc.perform(get("/admin/orders/{id}", orderId).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/orders/show"))
                .andExpect(model().attributeExists("order"))
                .andExpect(model().attribute("order", order));
    }

    @Test
    @WithMockUser(username = "testuser", roles = "ADMIN")
    void adminOrderDetailWithNonExistentOrderShowsError() throws Exception {
        Long nonExistentId = 999L;

        when(orderService.getOrder(nonExistentId))
                .thenThrow(new OrderNotFoundException(nonExistentId));

        mockMvc.perform(get("/admin/orders/{id}", nonExistentId))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/orders"))
                .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    @WithMockUser(username = "testuser", roles = "ADMIN")
    void adminOrderDetailWithServerErrorShowsError() throws Exception {
        Long orderId = 1L;

        when(orderService.getOrder(orderId))
                .thenThrow(new RuntimeException("Database error"));

        mockMvc.perform(get("/admin/orders/{id}", orderId))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/orders"))
                .andExpect(flash().attributeExists("errorMessage"));
    }

    // =========== Тесты для изменения статуса ===========

    @Test
    @WithMockUser(username = "testuser", roles = "ADMIN")
    void adminUpdateStatusChangesOrderStatus() throws Exception {
        Long orderId = 1L;
        String newStatus = "PAID";

        Order updated = createOrder(orderId, "PAID");
        when(orderService.updateStatus(orderId, OrderStatus.PAID)).thenReturn(updated);

        mockMvc.perform(post("/admin/orders/{id}/status", orderId)
                        .param("status", newStatus))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/orders/" + orderId))
                .andExpect(flash().attribute("successMessage",
                        String.format("Заказ #%s отмечен как оплаченный", updated.getOrderNumber())));
    }

    @Test
    @WithMockUser(username = "testuser", roles = "ADMIN")
    void adminUpdateStatusWithInvalidStatusShowsError() throws Exception {
        Long orderId = 1L;
        String invalidStatus = "INVALID_STATUS";

        mockMvc.perform(post("/admin/orders/{id}/status", orderId)
                        .param("status", invalidStatus))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/orders/" + orderId))
                .andExpect(flash().attribute("errorMessage", "Неверный статус заказа"));
    }

    @Test
    @WithMockUser(username = "testuser", roles = "ADMIN")
    void adminUpdateStatusWithEmptyStatusShowsError() throws Exception {
        Long orderId = 1L;

        mockMvc.perform(post("/admin/orders/{id}/status", orderId)
                        .param("status", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/orders/" + orderId))
                .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    @WithMockUser(username = "testuser", roles = "ADMIN")
    void adminUpdateStatusWithOrderNotFoundShowsError() throws Exception {
        Long orderId = 999L;
        String status = "PAID";

        when(orderService.updateStatus(orderId, OrderStatus.PAID))
                .thenThrow(new OrderNotFoundException(orderId));

        mockMvc.perform(post("/admin/orders/{id}/status", orderId)
                        .param("status", status))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/orders/" + orderId))
                .andExpect(flash().attribute("errorMessage",
                        String.format("Заказ с ID %d не найден", orderId)));
    }

    @Test
    @WithMockUser(username = "testuser", roles = "ADMIN")
    void adminUpdateStatusWithInvalidTransitionShowsError() throws Exception {
        Long orderId = 1L;
        String status = "COMPLETED";

        when(orderService.updateStatus(orderId, OrderStatus.COMPLETED))
                .thenThrow(new InvalidStatusTransitionException("CREATED", "COMPLETED"));

        mockMvc.perform(post("/admin/orders/{id}/status", orderId)
                        .param("status", status))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/orders/" + orderId))
                .andExpect(flash().attribute("errorMessage",
                        "Некорректный переход статуса: CREATED → COMPLETED"));
    }

    @Test
    @WithMockUser(username = "testuser", roles = "ADMIN")
    void adminUpdateStatusOfFinalizedOrderShowsError() throws Exception {
        Long orderId = 1L;
        String status = "PAID";

        when(orderService.updateStatus(orderId, OrderStatus.PAID))
                .thenThrow(new OrderFinalizedException("COMPLETED"));

        mockMvc.perform(post("/admin/orders/{id}/status", orderId)
                        .param("status", status))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/orders/" + orderId))
                .andExpect(flash().attribute("errorMessage",
                        "Невозможно изменить статус заказа: заказ уже завершен"));
    }

    // =========== Тесты для отмены заказа ===========

    @Test
    @WithMockUser(username = "testuser", roles = "ADMIN")
    void adminCancelOrderSuccessfully() throws Exception {
        Long orderId = 1L;
        Order cancelled = createOrder(orderId, "CANCELLED");

        when(orderService.updateStatus(orderId, OrderStatus.CANCELLED)).thenReturn(cancelled);

        mockMvc.perform(post("/admin/orders/{id}/cancel", orderId))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/orders/" + orderId))
                .andExpect(flash().attribute("successMessage",
                        String.format("Заказ #%s отменен", cancelled.getOrderNumber())));
    }

    @Test
    @WithMockUser(username = "testuser", roles = "ADMIN")
    void adminCancelNonExistentOrderShowsError() throws Exception {
        Long orderId = 999L;

        when(orderService.updateStatus(orderId, OrderStatus.CANCELLED))
                .thenThrow(new OrderNotFoundException(orderId));

        mockMvc.perform(post("/admin/orders/{id}/cancel", orderId))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/orders/" + orderId))
                .andExpect(flash().attributeExists("errorMessage"));
    }

    // =========== Тесты для добавления примечаний ===========

    @Test
    @WithMockUser(username = "testuser", roles = "ADMIN")
    void adminAddNoteToOrderSuccessfully() throws Exception {
        Long orderId = 1L;
        String note = "Позвонить клиенту завтра";

        doNothing().when(orderService).addNote(orderId, note);

        mockMvc.perform(post("/admin/orders/{id}/note", orderId)
                        .param("note", note))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/orders/" + orderId))
                .andExpect(flash().attribute("successMessage", "Примечание добавлено к заказу"));
    }

    @Test
    @WithMockUser(username = "testuser", roles = "ADMIN")
    void adminAddEmptyNoteShowsError() throws Exception {
        Long orderId = 1L;
        String emptyNote = "   ";

        doThrow(new OrderValidationException("note", "Примечание не может быть пустым"))
                .when(orderService).addNote(orderId, emptyNote.trim());

        mockMvc.perform(post("/admin/orders/{id}/note", orderId)
                        .param("note", emptyNote))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/orders/" + orderId))
                .andExpect(flash().attribute("errorMessage",
                        "Ошибка валидации поля 'note': Примечание не может быть пустым"));
    }

    @Test
    @WithMockUser(username = "testuser", roles = "ADMIN")
    void adminAddTooLongNoteShowsError() throws Exception {
        Long orderId = 1L;
        String longNote = "a".repeat(1001);

        doThrow(new OrderValidationException("note", "Примечание слишком длинное (максимум 1000 символов)"))
                .when(orderService).addNote(orderId, longNote.trim());

        mockMvc.perform(post("/admin/orders/{id}/note", orderId)
                        .param("note", longNote))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/orders/" + orderId))
                .andExpect(flash().attribute("errorMessage",
                        "Ошибка валидации поля 'note': Примечание слишком длинное (максимум 1000 символов)"));
    }

    @Test
    @WithMockUser(username = "testuser", roles = "ADMIN")
    void adminAddNoteToNonExistentOrderShowsError() throws Exception {
        Long orderId = 999L;
        String note = "Тестовое примечание";

        doThrow(new OrderNotFoundException(orderId))
                .when(orderService).addNote(orderId, note);

        mockMvc.perform(post("/admin/orders/{id}/note", orderId)
                        .param("note", note))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/orders/" + orderId))
                .andExpect(flash().attributeExists("errorMessage"));
    }

    // =========== Вспомогательные методы ===========

    private Order createOrder(Long id, String status) {
        Order order = new Order();
        order.setId(id);
        order.setOrderNumber(10000L + id);
        order.setStatus(OrderStatus.valueOf(status));
        order.setTotalPrice(BigDecimal.valueOf(1000));
        order.setCreatedAt(Instant.now());
        order.setUpdatedAt(Instant.now());

        // Создаем пользователя
        User user = new User();
        user.setId(1L);
        user.setEmail("user@example.com");
        user.setFirstName("John");
        user.setLastName("Doe");
        order.setUser(user);

        // Создаем OrderItems с заполненными полями
        OrderItem orderItem = new OrderItem();
        orderItem.setId(1L);
        orderItem.setQuantity(2);
        orderItem.setUnitPrice(BigDecimal.valueOf(500));
        orderItem.setProductName("Тестовый товар");

        // Если нужно связать с Product
        Product product = new Product();
        product.setId(1L);
        product.setName("Тестовый товар");
        product.setPrice(BigDecimal.valueOf(500));
        orderItem.setProduct(product);

        order.setOrderItems(Set.of(orderItem));

        return order;
    }

}
