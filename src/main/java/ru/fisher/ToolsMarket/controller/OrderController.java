package ru.fisher.ToolsMarket.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.fisher.ToolsMarket.dto.CartItemDto;
import ru.fisher.ToolsMarket.dto.OrderItemDto;
import ru.fisher.ToolsMarket.exceptions.OrderFinalizedException;
import ru.fisher.ToolsMarket.exceptions.OrderNotFoundException;
import ru.fisher.ToolsMarket.models.*;
import ru.fisher.ToolsMarket.service.*;

import java.math.BigDecimal;
import java.util.List;

@Controller
@RequestMapping("/order")
@RequiredArgsConstructor
@Slf4j
public class OrderController {

    private final OrderService orderService;
    private final CartService cartService;
    private final UserService userService;

    @GetMapping("/{orderId}")
    public String viewOrder(@PathVariable Long orderId,
                            Model model,
                            Authentication authentication) {
        Long userId = getCurrentUserId(authentication);
        if (userId == null) {
            return "redirect:/auth/login";
        }

        try {
            User currentUser = userService.findById(userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден"));

            Order order = orderService.getOrderWithProducts(orderId);

            // Проверяем, что заказ принадлежит пользователю
            if (!order.getUser().getId().equals(userId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Доступ запрещен");
            }

            List<OrderItemDto> orderItemDtos = order.getOrderItems()
                    .stream()
                    .map(OrderItemDto::fromEntity)
                    .toList();

            // Рассчитываем итоги из сохраненных данных
            BigDecimal originalTotal = BigDecimal.ZERO;
            BigDecimal totalDiscount = BigDecimal.ZERO;

            for (OrderItemDto dto : orderItemDtos) {
                BigDecimal itemOriginalTotal = dto.getOriginalPrice()
                        .multiply(BigDecimal.valueOf(dto.getQuantity()));
                originalTotal = originalTotal.add(itemOriginalTotal);

                if (dto.isHasDiscount() && dto.getDiscountAmount() != null) {
                    totalDiscount = totalDiscount.add(dto.getDiscountAmount());
                }
            }

            boolean canCancel = canCancelOrder(order);

            model.addAttribute("order", order);
            model.addAttribute("orderItems", orderItemDtos);
            model.addAttribute("canCancel", canCancel);
            model.addAttribute("currentUser", currentUser);
            model.addAttribute("isPublicView", true);
            model.addAttribute("originalTotal", originalTotal);
            model.addAttribute("totalDiscount", totalDiscount);
            model.addAttribute("hasDiscounts", totalDiscount.compareTo(BigDecimal.ZERO) > 0);

            // Добавляем тип пользователя для отображения
            if (order.getUser() != null && order.getUser().getUserType() != null) {
                model.addAttribute("userTypeDisplay", order.getUser().getUserType().getDisplayName());
            }

            return "order/index";

        } catch (OrderNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Заказ не найден");
        } catch (Exception e) {
            log.error("Ошибка при получении заказа: id={}", orderId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Ошибка сервера");
        }
    }

    /**
     * Проверка возможности отмены заказа
     */
    private boolean canCancelOrder(Order order) {
        return order.getStatus() == OrderStatus.CREATED ||
                order.getStatus() == OrderStatus.PAID;
    }

    @GetMapping("/checkout")
    public String checkout(@AuthenticationPrincipal UserDetails userDetails,
                           Model model) {

        if (userDetails == null) {
            return "redirect:/auth/login";
        }

        User user = userService.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден"));

        // Получаем товары из корзины с учетом скидок
        List<CartItemDto> items = cartService.getUserCartItems(user.getId());

        // Вычисляем суммы
        BigDecimal totalAmount = items.stream()
                .map(item -> item.getUnitPrice() != null ?
                        item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())) : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalWithDiscount = items.stream()
                .map(CartItemDto::getTotalPriceWithDiscount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalDiscount = totalAmount.subtract(totalWithDiscount);

        boolean hasDiscounts = totalDiscount.compareTo(BigDecimal.ZERO) > 0;

        model.addAttribute("items", items);
        model.addAttribute("totalAmount", totalAmount);
        model.addAttribute("totalWithDiscount", totalWithDiscount);
        model.addAttribute("totalDiscount", totalDiscount);
        model.addAttribute("hasDiscounts", hasDiscounts);
        model.addAttribute("currentUser", user);
        model.addAttribute("userTypeDisplay", user.getUserType().getDisplayName());

        return "order/checkout";
    }

    @PostMapping("/create")
    public String createOrder(RedirectAttributes redirectAttributes,
                              Authentication authentication) {
        Long userId = getCurrentUserId(authentication);
        if (userId == null) {
            return "redirect:/auth/login";
        }

        try {
            // Проверяем, что корзина не пуста
            List<CartItemDto> items = cartService.getUserCartItems(userId);
            if (items.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "Невозможно создать заказ из пустой корзины");
                return "redirect:/cart";
            }

            // Создаем заказ для зарегистрированного пользователя
            Order order = orderService.createOrderFromUserCart(userId);

            redirectAttributes.addFlashAttribute("successMessage",
                    "Заказ №" + order.getOrderNumber() + " успешно создан!");

            return "redirect:/order/" + order.getId();

        } catch (IllegalStateException e) {
            log.warn("Попытка создать заказ из пустой корзины: userId={}", userId);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Невозможно создать заказ из пустой корзины");
            return "redirect:/cart";
        } catch (Exception e) {
            log.error("Ошибка при создании заказа: userId={}", userId, e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Произошла ошибка при создании заказа");
            return "redirect:/cart";
        }
    }

    @PostMapping("/{orderId}/cancel")
    public String cancelOrder(@PathVariable Long orderId,
                              RedirectAttributes redirectAttributes,
                              Authentication authentication) {
        try {
            Long userId = getCurrentUserId(authentication);

            if (userId != null) {
                // Проверяем, что заказ принадлежит пользователю
                Order order = orderService.getUserOrder(orderId, userId);
                if (!canCancelOrder(order)) {
                    redirectAttributes.addFlashAttribute("errorMessage",
                            "Вы не можете отменить этот заказ");
                    return "redirect:/order/" + orderId;
                }
            }

            orderService.updateStatus(orderId, OrderStatus.CANCELLED);
            redirectAttributes.addFlashAttribute("successMessage", "Заказ отменен");

        } catch (OrderNotFoundException e) {
            log.warn("Заказ не найден при отмене: id={}", orderId);
            redirectAttributes.addFlashAttribute("errorMessage", "Заказ не найден");
        } catch (OrderFinalizedException e) {
            log.warn("Попытка отменить завершенный заказ: id={}", orderId);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            log.error("Ошибка при отмене заказа: id={}", orderId, e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Произошла ошибка при отмене заказа");
        }

        return "redirect:/order/" + orderId;
    }

    /**
     * Страница истории заказов для авторизованных пользователей
     */
    @GetMapping("/history")
    public String orderHistory(Model model, Authentication authentication) {
        Long userId = getCurrentUserId(authentication);
        if (userId == null) {
            return "redirect:/auth/login";
        }

        try {
            List<Order> orders = orderService.getUserOrders(userId);
            model.addAttribute("orders", orders);
            model.addAttribute("user", userService.findById(userId).orElse(null));
            return "order/history";

        } catch (Exception e) {
            log.error("Ошибка при получении истории заказов: userId={}", userId, e);
            model.addAttribute("error", "Ошибка при загрузке истории заказов");
            return "order/history";
        }
    }

    // =========== Вспомогательные методы ===========

    private Long getCurrentUserId(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof UserDetails) {
                String username = ((UserDetails) principal).getUsername();
                return userService.findByUsername(username)
                        .map(User::getId)
                        .orElse(null);
            }
        }
        return null;
    }

}
