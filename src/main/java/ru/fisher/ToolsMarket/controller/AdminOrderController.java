package ru.fisher.ToolsMarket.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.fisher.ToolsMarket.exceptions.InvalidStatusTransitionException;
import ru.fisher.ToolsMarket.exceptions.OrderFinalizedException;
import ru.fisher.ToolsMarket.exceptions.OrderNotFoundException;
import ru.fisher.ToolsMarket.exceptions.OrderValidationException;
import ru.fisher.ToolsMarket.models.Order;
import ru.fisher.ToolsMarket.models.OrderStatus;
import ru.fisher.ToolsMarket.service.OrderService;
import ru.fisher.ToolsMarket.service.UserService;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/orders")
@RequiredArgsConstructor
@Slf4j
public class AdminOrderController {

    private final OrderService orderService;
    private final UserService userService;

    private static final String SUCCESS_MSG = "successMessage";
    private static final String ERROR_MSG = "errorMessage";
    private static final String REDIRECT_ORDER_DETAILS = "redirect:/admin/orders/";
    private static final String REDIRECT_ORDERS_LIST = "redirect:/admin/orders";


    @GetMapping
    public String listOrders(@RequestParam(required = false) String status,
                             @RequestParam(required = false) String search,
                             @RequestParam(required = false) Long userId, // Добавил параметр userId
                             Model model) {
        try {
            List<Order> orders = getFilteredOrders(status, search, userId); // Добавил userId
            addOrderStatisticsToModel(model);

            // Добавляем список пользователей для фильтрации
            model.addAttribute("users", userService.findAll());
            model.addAttribute("orders", orders);
            model.addAttribute("searchQuery", search);
            model.addAttribute("selectedUserId", userId); // Добавил для сохранения выбора в форме

            // Если фильтруем по пользователю, добавляем информацию о нем
            if (userId != null) {
                userService.findById(userId).ifPresent(user -> {
                    model.addAttribute("selectedUser", user);
                });
            }

            return "admin/orders/index";

        } catch (Exception e) {
            log.error("Ошибка при получении списка заказов: статус={}, поиск={}, userId={}",
                    status, search, userId, e);
            model.addAttribute(ERROR_MSG, "Ошибка при загрузке списка заказов");
            return "admin/orders/index";
        }
    }

    @GetMapping("/{id}")
    public String showOrder(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        try {
            Order order = orderService.getOrder(id);
            model.addAttribute("order", order);
            return "admin/orders/show";

        } catch (OrderNotFoundException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/orders";
        } catch (Exception e) {
            log.error("Ошибка при получении заказа: id={}", id, e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Ошибка при загрузке заказа");
            return "redirect:/admin/orders";
        }
    }

    // Все остальные методы остаются БЕЗ изменений...

    @PostMapping("/{id}/status")
    public String updateOrderStatus(@PathVariable Long id,
                                    @RequestParam String status,
                                    RedirectAttributes redirectAttributes) {
        try {
            validateStatusParam(status);
            OrderStatus newStatus = OrderStatus.valueOf(status.toUpperCase());
            Order updated = orderService.updateStatus(id, newStatus);

            addSuccessMessage(redirectAttributes,
                    getStatusUpdateMessage(newStatus, updated.getOrderNumber()));

            log.info("Статус заказа обновлен: id={}, новый статус={}", id, newStatus);

        } catch (OrderNotFoundException e) {
            log.warn("Заказ не найден при обновлении статуса: id={}", id);
            addErrorMessage(redirectAttributes, e.getMessage());
        } catch (OrderFinalizedException e) {
            log.warn("Попытка изменить завершенный заказ: id={}", id);
            addErrorMessage(redirectAttributes, e.getMessage());
        } catch (InvalidStatusTransitionException e) {
            log.warn("Некорректный переход статуса: id={}, причина={}", id, e.getMessage());
            addErrorMessage(redirectAttributes, e.getMessage());
        } catch (OrderValidationException e) {
            log.warn("Ошибка валидации: id={}, причина={}", id, e.getMessage());
            addErrorMessage(redirectAttributes, e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("Неверный статус заказа: id={}, статус={}", id, status, e);
            addErrorMessage(redirectAttributes, "Неверный статус заказа");
        } catch (Exception e) {
            log.error("Ошибка при обновлении статуса заказа: id={}, статус={}", id, status, e);
            addErrorMessage(redirectAttributes, "Ошибка при обновлении статуса");
        }

        return REDIRECT_ORDER_DETAILS + id;
    }

    @PostMapping("/{id}/cancel")
    public String cancelOrder(@PathVariable Long id,
                              RedirectAttributes redirectAttributes) {
        try {
            Order cancelled = orderService.updateStatus(id, OrderStatus.CANCELLED);

            addSuccessMessage(redirectAttributes,
                    String.format("Заказ #%s отменен", cancelled.getOrderNumber()));

            log.info("Заказ отменен: id={}, номер={}", id, cancelled.getOrderNumber());

        } catch (OrderNotFoundException e) {
            log.warn("Заказ не найден при отмене: id={}", id);
            addErrorMessage(redirectAttributes, e.getMessage());
        } catch (OrderFinalizedException e) {
            log.warn("Попытка отменить завершенный заказ: id={}", id);
            addErrorMessage(redirectAttributes, e.getMessage());
        } catch (InvalidStatusTransitionException e) {
            log.warn("Невозможно отменить заказ: id={}, причина={}", id, e.getMessage());
            addErrorMessage(redirectAttributes, e.getMessage());
        } catch (Exception e) {
            log.error("Ошибка при отмене заказа: id={}", id, e);
            addErrorMessage(redirectAttributes, "Ошибка при отмене заказа");
        }

        return REDIRECT_ORDER_DETAILS + id;
    }

    @PostMapping("/{id}/note")
    public String addNote(@PathVariable Long id,
                          @RequestParam String note,
                          RedirectAttributes redirectAttributes) {
        try {
            orderService.addNote(id, note.trim());

            addSuccessMessage(redirectAttributes, "Примечание добавлено к заказу");
            log.info("Добавлено примечание к заказу: id={}", id);

        } catch (OrderNotFoundException e) {
            log.warn("Заказ не найден при добавлении примечания: id={}", id);
            addErrorMessage(redirectAttributes, e.getMessage());
        } catch (OrderValidationException e) {
            log.warn("Ошибка валидации примечания: id={}, причина={}", id, e.getMessage());
            addErrorMessage(redirectAttributes, e.getMessage());
        } catch (Exception e) {
            log.error("Ошибка при добавлении примечания: id={}", id, e);
            addErrorMessage(redirectAttributes, "Ошибка при добавлении примечания");
        }

        return REDIRECT_ORDER_DETAILS + id;
    }

    @GetMapping("/statistics")
    public String showStatistics(Model model) {
        try {
            Map<String, Long> statusCounts = getStatusCounts();
            model.addAttribute("statusCounts", statusCounts);
            model.addAttribute("totalOrders", orderService.countAllOrders());
            model.addAttribute("totalRevenue", orderService.calculateTotalRevenue());

            return "admin/orders/statistics";

        } catch (Exception e) {
            log.error("Ошибка при загрузке статистики", e);
            return handleGeneralError("Ошибка при загрузке статистики", model);
        }
    }

    // =========== Вспомогательные приватные методы ===========

    private List<Order> getFilteredOrders(String status, String search, Long userId) {
        // Добавил userId параметр
        if (userId != null) {
            // Фильтруем по пользователю
            return orderService.getUserOrders(userId);
        } else if (StringUtils.hasText(search)) {
            return searchOrders(search.trim());
        } else if (StringUtils.hasText(status)) {
            OrderStatus orderStatus = OrderStatus.valueOf(status);
            return orderService.getOrdersByStatus(orderStatus);
        } else {
            return orderService.getAllOrders();
        }
    }

    private List<Order> searchOrders(String searchQuery) {
        try {
            Long orderNumber = Long.parseLong(searchQuery);
            Order order = orderService.findByOrderNumber(orderNumber);
            return order != null ? List.of(order) : List.of();
        } catch (OrderNotFoundException e) {
            log.info("Заказ не найден по номеру: {}", searchQuery);
            return List.of();
        } catch (NumberFormatException e) {
            return orderService.searchOrders(searchQuery);
        }
    }

    private void addOrderStatisticsToModel(Model model) {
        model.addAttribute("newOrdersCount", orderService.countOrdersByStatus(OrderStatus.CREATED));
        model.addAttribute("paidOrdersCount", orderService.countOrdersByStatus(OrderStatus.PAID));
        model.addAttribute("completedOrdersCount", orderService.countOrdersByStatus(OrderStatus.COMPLETED));
        model.addAttribute("cancelledOrdersCount", orderService.countOrdersByStatus(OrderStatus.CANCELLED));
    }

    private Map<String, Long> getStatusCounts() {
        return Arrays.stream(OrderStatus.values())
                .collect(Collectors.toMap(
                        OrderStatus::name,
                        orderService::countOrdersByStatus,
                        (v1, v2) -> v1,
                        LinkedHashMap::new
                ));
    }

    private String getStatusUpdateMessage(OrderStatus status, Long orderNumber) {
        return switch (status) {
            case CREATED -> String.format("Заказ #%s возвращен в статус 'Создан'", orderNumber);
            case PROCESSING -> String.format("Заказ #%s переведен в обработку", orderNumber);
            case PAID -> String.format("Заказ #%s отмечен как оплаченный", orderNumber);
            case COMPLETED -> String.format("Заказ #%s завершен", orderNumber);
            case CANCELLED -> String.format("Заказ #%s отменен", orderNumber);
        };
    }

    private void validateStatusParam(String status) {
        if (!StringUtils.hasText(status)) {
            throw new OrderValidationException("status", "Статус не может быть пустым");
        }
    }

    private String handleOrderNotFound(Long id, Model model) {
        model.addAttribute(ERROR_MSG, String.format("Заказ #%d не найден", id));
        return REDIRECT_ORDERS_LIST;
    }

    private String handleGeneralError(String message, Model model) {
        model.addAttribute(ERROR_MSG, message);
        return REDIRECT_ORDERS_LIST;
    }

    private void addSuccessMessage(RedirectAttributes redirectAttributes, String message) {
        redirectAttributes.addFlashAttribute(SUCCESS_MSG, message);
    }

    private void addErrorMessage(RedirectAttributes redirectAttributes, String message) {
        redirectAttributes.addFlashAttribute(ERROR_MSG, message);
    }
}

