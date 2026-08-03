package ru.fisher.ToolsMarket.controller.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.fisher.ToolsMarket.dto.OrderDTO.OrderAdminDto;
import ru.fisher.ToolsMarket.dto.OrderDTO.OrderItemDto;
import ru.fisher.ToolsMarket.dto.OrderDTO.OrderStatisticsDto;
import ru.fisher.ToolsMarket.dto.UserDTO.UserFilterDto;
import ru.fisher.ToolsMarket.exceptions.InvalidStatusTransitionException;
import ru.fisher.ToolsMarket.exceptions.OrderFinalizedException;
import ru.fisher.ToolsMarket.exceptions.OrderNotFoundException;
import ru.fisher.ToolsMarket.exceptions.OrderValidationException;
import ru.fisher.ToolsMarket.models.Order;
import ru.fisher.ToolsMarket.models.OrderStatus;
import ru.fisher.ToolsMarket.service.DiscountService;
import ru.fisher.ToolsMarket.service.OrderService;
import ru.fisher.ToolsMarket.service.UserService;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/orders")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class AdminOrderController {

    private final OrderService orderService;
    private final UserService userService;
    private final DiscountService discountService;

    private static final String SUCCESS_MSG = "successMessage";
    private static final String ERROR_MSG = "errorMessage";
    private static final String REDIRECT_ORDERS_LIST = "redirect:/admin/orders/";


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
    public String showOrder(@PathVariable Long id,
                            Model model,
                            RedirectAttributes redirectAttributes) {
        try {
            Order order = orderService.getOrderWithProducts(id);

            List<OrderItemDto> orderItemDtos = order.getOrderItems()
                    .stream()
                    .map(OrderItemDto::fromEntity)
                    .toList();

            // Рассчитываем итоги как у пользователя
            BigDecimal originalTotal = orderItemDtos.stream()
                    .map(OrderItemDto::getTotalWithoutDiscount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Проверяем hasDiscount перед получением discountAmount
            BigDecimal totalDiscount = orderItemDtos.stream()
                    .map(dto -> dto.isHasDiscount() && dto.getDiscountAmount() != null
                            ? dto.getDiscountAmount()
                            : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            boolean hasDiscounts = totalDiscount.compareTo(BigDecimal.ZERO) > 0;

            // Логируем для отладки
            log.debug("Заказ #{}: товаров={}, скидка={} руб",
                    order.getOrderNumber(),
                    orderItemDtos.size(),
                    totalDiscount);

            if (!orderItemDtos.isEmpty()) {
                OrderItemDto firstItem = orderItemDtos.getFirst();
                log.debug("Первый товар: productId={}, name={}, hasDiscount={}",
                        firstItem.getProductId(),
                        firstItem.getProductName(),
                        firstItem.isHasDiscount());

                // Дополнительная отладка для цен
                log.debug("Цены: original={}, unit={}, discountAmount={}",
                        firstItem.getOriginalPrice(),
                        firstItem.getUnitPrice(),
                        firstItem.getDiscountAmount());
            }

            model.addAttribute("order", order);
            model.addAttribute("orderItems", orderItemDtos);
            model.addAttribute("originalTotal", originalTotal);
            model.addAttribute("totalDiscount", totalDiscount);
            model.addAttribute("hasDiscounts", hasDiscounts);

            // Добавляем тип пользователя если есть
            if (order.getUser() != null && order.getUser().getUserType() != null) {
                model.addAttribute("userTypeDisplay", order.getUser().getUserType().getDisplayName());
            }

            return "admin/orders/show";

        } catch (OrderNotFoundException e) {
            handleOrderNotFound(id, model);
            return REDIRECT_ORDERS_LIST;
        } catch (Exception e) {
            log.error("Ошибка при получении заказа: id={}", id, e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Ошибка при загрузке заказа");
            return REDIRECT_ORDERS_LIST;
        }
    }

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

        return REDIRECT_ORDERS_LIST + id;
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

        return REDIRECT_ORDERS_LIST + id;
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

        return REDIRECT_ORDERS_LIST + id;
    }

    // =========== Вспомогательные приватные методы ===========

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

    private void addSuccessMessage(RedirectAttributes redirectAttributes, String message) {
        redirectAttributes.addFlashAttribute(SUCCESS_MSG, message);
    }

    private void addErrorMessage(RedirectAttributes redirectAttributes, String message) {
        redirectAttributes.addFlashAttribute(ERROR_MSG, message);
    }
}

