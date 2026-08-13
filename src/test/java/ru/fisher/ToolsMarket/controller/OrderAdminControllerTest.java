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
import ru.fisher.ToolsMarket.dto.OrderDTO.OrderAdminDto;
import ru.fisher.ToolsMarket.dto.OrderDTO.OrderStatisticsDto;
import ru.fisher.ToolsMarket.exceptions.InvalidStatusTransitionException;
import ru.fisher.ToolsMarket.exceptions.OrderFinalizedException;
import ru.fisher.ToolsMarket.exceptions.OrderNotFoundException;
import ru.fisher.ToolsMarket.exceptions.OrderValidationException;
import ru.fisher.ToolsMarket.models.Order;
import ru.fisher.ToolsMarket.models.OrderItem;
import ru.fisher.ToolsMarket.models.OrderStatus;
import ru.fisher.ToolsMarket.models.Product;
import ru.fisher.ToolsMarket.models.User;
import ru.fisher.ToolsMarket.models.UserType;
import ru.fisher.ToolsMarket.service.OrderService;
import ru.fisher.ToolsMarket.service.UserService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

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

    private OrderStatisticsDto emptyStats() {
        return OrderStatisticsDto.builder().build();
    }

    private OrderAdminDto adminDto(Long id, Long orderNumber, String status) {
        return OrderAdminDto.builder()
                .id(id)
                .orderNumber(orderNumber)
                .status(status)
                .totalPrice(BigDecimal.valueOf(1000))
                .createdAt(Instant.now())
                .build();
    }

    private Order createOrder(Long id, String status) {
        Product product = new Product();
        product.setId(1L);
        product.setName("Тестовый товар");
        product.setTitle("Тестовый товар");
        product.setImages(new LinkedHashSet<>());

        OrderItem orderItem = OrderItem.builder()
                .id(1L)
                .product(product)
                .productName("Тестовый товар")
                .productSku("SKU-1")
                .quantity(2)
                .unitPrice(BigDecimal.valueOf(500))
                .subtotal(BigDecimal.valueOf(1000))
                .build();

        User user = User.builder()
                .id(1L)
                .username("user")
                .email("user@example.com")
                .userType(UserType.REGULAR)
                .build();

        Order order = Order.builder()
                .id(id)
                .orderNumber(10000L + id)
                .status(OrderStatus.valueOf(status))
                .totalPrice(BigDecimal.valueOf(1000))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .user(user)
                .orderItems(Set.of(orderItem))
                .build();
        orderItem.setOrder(order);
        return order;
    }

    // =========== Тесты для списка заказов ===========

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminOrderListReturnsAllOrders() throws Exception {
        List<OrderAdminDto> orders = List.of(
                adminDto(1L, 10001L, "CREATED"),
                adminDto(2L, 10002L, "PAID")
        );
        OrderStatisticsDto stats = OrderStatisticsDto.builder()
                .newOrdersCount(1)
                .paidOrdersCount(1)
                .build();

        when(orderService.getOrdersForAdmin(null, null, null)).thenReturn(orders);
        when(orderService.getOrderStatistics(null, null, null)).thenReturn(stats);
        when(orderService.getUsersForOrderFilter()).thenReturn(List.of());

        mockMvc.perform(get("/admin/orders").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/orders/index"))
                .andExpect(model().attributeExists("orders", "users"))
                .andExpect(model().attribute("orders", orders))
                .andExpect(model().attribute("newOrdersCount", 1L))
                .andExpect(model().attribute("paidOrdersCount", 1L));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminOrderListWithStatusFilterReturnsFilteredOrders() throws Exception {
        List<OrderAdminDto> paidOrders = List.of(adminDto(2L, 10002L, "PAID"));

        when(orderService.getOrdersForAdmin("PAID", null, null)).thenReturn(paidOrders);
        when(orderService.getOrderStatistics("PAID", null, null)).thenReturn(emptyStats());
        when(orderService.getUsersForOrderFilter()).thenReturn(List.of());

        mockMvc.perform(get("/admin/orders").with(csrf())
                        .param("status", "PAID"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/orders/index"))
                .andExpect(model().attribute("orders", paidOrders))
                .andExpect(model().attribute("selectedStatus", "PAID"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminOrderListWithSearchByOrderNumber() throws Exception {
        String search = "123456";
        List<OrderAdminDto> found = List.of(adminDto(1L, 123456L, "CREATED"));

        when(orderService.getOrdersForAdmin(null, search, null)).thenReturn(found);
        when(orderService.getOrderStatistics(null, search, null)).thenReturn(emptyStats());
        when(orderService.getUsersForOrderFilter()).thenReturn(List.of());

        mockMvc.perform(get("/admin/orders").with(csrf())
                        .param("search", search))
                .andExpect(status().isOk())
                .andExpect(model().attribute("orders", found))
                .andExpect(model().attribute("searchQuery", search));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminOrderListWithSearchByProductSku() throws Exception {
        String search = "TOOL-123";
        List<OrderAdminDto> found = List.of(adminDto(1L, 10001L, "CREATED"));

        when(orderService.getOrdersForAdmin(null, search, null)).thenReturn(found);
        when(orderService.getOrderStatistics(null, search, null)).thenReturn(emptyStats());
        when(orderService.getUsersForOrderFilter()).thenReturn(List.of());

        mockMvc.perform(get("/admin/orders").with(csrf())
                        .param("search", search))
                .andExpect(status().isOk())
                .andExpect(model().attribute("orders", found));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminOrderListEmptyReturnsEmptyList() throws Exception {
        when(orderService.getOrdersForAdmin(null, null, null)).thenReturn(List.of());
        when(orderService.getOrderStatistics(null, null, null)).thenReturn(emptyStats());
        when(orderService.getUsersForOrderFilter()).thenReturn(List.of());

        mockMvc.perform(get("/admin/orders").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("orders", List.of()));
    }

    // =========== Тесты для деталей заказа ===========

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminOrderDetailReturnsOrderWithItems() throws Exception {
        Long orderId = 1L;
        Order order = createOrder(orderId, "CREATED");

        when(orderService.getOrderWithProducts(orderId)).thenReturn(order);

        mockMvc.perform(get("/admin/orders/{id}", orderId).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/orders/show"))
                .andExpect(model().attributeExists("order", "orderItems"))
                .andExpect(model().attribute("order", order));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminOrderDetailWithNonExistentOrderRedirectsToList() throws Exception {
        Long nonExistentId = 999L;

        when(orderService.getOrderWithProducts(nonExistentId))
                .thenThrow(new OrderNotFoundException(nonExistentId));

        mockMvc.perform(get("/admin/orders/{id}", nonExistentId).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/orders/"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminOrderDetailWithServerErrorShowsError() throws Exception {
        Long orderId = 1L;

        when(orderService.getOrderWithProducts(orderId))
                .thenThrow(new RuntimeException("Database error"));

        mockMvc.perform(get("/admin/orders/{id}", orderId).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/orders/"))
                .andExpect(flash().attributeExists("errorMessage"));
    }

    // =========== Тесты для изменения статуса ===========

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminUpdateStatusChangesOrderStatus() throws Exception {
        Long orderId = 1L;
        String newStatus = "PAID";

        Order updated = createOrder(orderId, "PAID");
        when(orderService.updateStatus(orderId, OrderStatus.PAID)).thenReturn(updated);

        mockMvc.perform(post("/admin/orders/{id}/status", orderId)
                        .with(csrf())
                        .param("status", newStatus))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/orders/" + orderId))
                .andExpect(flash().attribute("successMessage",
                        String.format("Заказ #%s отмечен как оплаченный", updated.getOrderNumber())));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminUpdateStatusWithInvalidStatusShowsError() throws Exception {
        Long orderId = 1L;
        String invalidStatus = "INVALID_STATUS";

        mockMvc.perform(post("/admin/orders/{id}/status", orderId)
                        .with(csrf())
                        .param("status", invalidStatus))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/orders/" + orderId))
                .andExpect(flash().attribute("errorMessage", "Неверный статус заказа"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminUpdateStatusWithEmptyStatusShowsError() throws Exception {
        Long orderId = 1L;

        mockMvc.perform(post("/admin/orders/{id}/status", orderId)
                        .with(csrf())
                        .param("status", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/orders/" + orderId))
                .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminUpdateStatusWithOrderNotFoundShowsError() throws Exception {
        Long orderId = 999L;
        String status = "PAID";

        when(orderService.updateStatus(orderId, OrderStatus.PAID))
                .thenThrow(new OrderNotFoundException(orderId));

        mockMvc.perform(post("/admin/orders/{id}/status", orderId)
                        .with(csrf())
                        .param("status", status))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/orders/" + orderId))
                .andExpect(flash().attribute("errorMessage",
                        String.format("Заказ с ID %d не найден", orderId)));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminUpdateStatusWithInvalidTransitionShowsError() throws Exception {
        Long orderId = 1L;
        String status = "COMPLETED";

        when(orderService.updateStatus(orderId, OrderStatus.COMPLETED))
                .thenThrow(new InvalidStatusTransitionException("CREATED", "COMPLETED"));

        mockMvc.perform(post("/admin/orders/{id}/status", orderId)
                        .with(csrf())
                        .param("status", status))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/orders/" + orderId))
                .andExpect(flash().attribute("errorMessage",
                        "Некорректный переход статуса: CREATED → COMPLETED"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminUpdateStatusOfFinalizedOrderShowsError() throws Exception {
        Long orderId = 1L;
        String status = "PAID";

        when(orderService.updateStatus(orderId, OrderStatus.PAID))
                .thenThrow(new OrderFinalizedException("COMPLETED"));

        mockMvc.perform(post("/admin/orders/{id}/status", orderId)
                        .with(csrf())
                        .param("status", status))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/orders/" + orderId))
                .andExpect(flash().attribute("errorMessage",
                        "Невозможно изменить статус заказа: заказ уже завершен"));
    }

    // =========== Тесты для отмены заказа ===========

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminCancelOrderSuccessfully() throws Exception {
        Long orderId = 1L;
        Order cancelled = createOrder(orderId, "CANCELLED");

        when(orderService.updateStatus(orderId, OrderStatus.CANCELLED)).thenReturn(cancelled);

        mockMvc.perform(post("/admin/orders/{id}/cancel", orderId).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/orders/" + orderId))
                .andExpect(flash().attribute("successMessage",
                        String.format("Заказ #%s отменен", cancelled.getOrderNumber())));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminCancelNonExistentOrderShowsError() throws Exception {
        Long orderId = 999L;

        when(orderService.updateStatus(orderId, OrderStatus.CANCELLED))
                .thenThrow(new OrderNotFoundException(orderId));

        mockMvc.perform(post("/admin/orders/{id}/cancel", orderId).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/orders/" + orderId))
                .andExpect(flash().attributeExists("errorMessage"));
    }

    // =========== Тесты для добавления примечаний ===========

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminAddNoteToOrderSuccessfully() throws Exception {
        Long orderId = 1L;
        String note = "Позвонить клиенту завтра";

        doNothing().when(orderService).addNote(orderId, note);

        mockMvc.perform(post("/admin/orders/{id}/note", orderId)
                        .with(csrf())
                        .param("note", note))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/orders/" + orderId))
                .andExpect(flash().attribute("successMessage", "Примечание добавлено к заказу"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminAddEmptyNoteShowsError() throws Exception {
        Long orderId = 1L;
        String emptyNote = "   ";

        doThrow(new OrderValidationException("note", "Примечание не может быть пустым"))
                .when(orderService).addNote(orderId, "");

        mockMvc.perform(post("/admin/orders/{id}/note", orderId)
                        .with(csrf())
                        .param("note", emptyNote))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/orders/" + orderId))
                .andExpect(flash().attribute("errorMessage",
                        "Ошибка валидации поля 'note': Примечание не может быть пустым"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminAddTooLongNoteShowsError() throws Exception {
        Long orderId = 1L;
        String longNote = "a".repeat(1001);

        doThrow(new OrderValidationException("note", "Примечание слишком длинное (максимум 1000 символов)"))
                .when(orderService).addNote(orderId, longNote);

        mockMvc.perform(post("/admin/orders/{id}/note", orderId)
                        .with(csrf())
                        .param("note", longNote))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/orders/" + orderId))
                .andExpect(flash().attribute("errorMessage",
                        "Ошибка валидации поля 'note': Примечание слишком длинное (максимум 1000 символов)"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminAddNoteToNonExistentOrderShowsError() throws Exception {
        Long orderId = 999L;
        String note = "Тестовое примечание";

        doThrow(new OrderNotFoundException(orderId))
                .when(orderService).addNote(orderId, note);

        mockMvc.perform(post("/admin/orders/{id}/note", orderId)
                        .with(csrf())
                        .param("note", note))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/orders/" + orderId))
                .andExpect(flash().attributeExists("errorMessage"));
    }
}
