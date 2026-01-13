package ru.fisher.ToolsMarket.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
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
import java.math.RoundingMode;
import java.util.List;

@Controller
@RequestMapping("/order")
@RequiredArgsConstructor
@Slf4j
public class OrderController {

    private final OrderService orderService;
    private final CartService cartService;
    private final UserService userService;
    private final ProductService productService;
    private final DiscountService discountService;

    @GetMapping("/{orderId}")
    public String viewOrder(@PathVariable Long orderId,
                            Model model,
                            Authentication authentication) {
        try {
            Long userId = getCurrentUserId(authentication);
            Order order;

            if (userId != null) {
                order = orderService.getOrderWithProducts(orderId);

                if (!order.getUser().getId().equals(userId)) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Доступ запрещен");
                }
            } else {
                order = orderService.getOrderWithProducts(orderId);
            }

            List<OrderItemDto> orderItemDtos = order.getOrderItems()
                    .stream()
                    .map(OrderItemDto::fromEntity) // ← БЕЗ discountService!
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

            boolean canCancel = canCancelOrder(order, userId);

            model.addAttribute("order", order);
            model.addAttribute("orderItems", orderItemDtos);
            model.addAttribute("canCancel", canCancel);
            model.addAttribute("isAuthenticated", userId != null);
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

    private boolean canCancelOrder(Order order, Long userId) {
        // Проверяем, может ли пользователь отменить заказ
        if (order.getStatus() != OrderStatus.CREATED &&
                order.getStatus() != OrderStatus.PAID) {
            return false;
        }

        // Проверяем принадлежность заказа
        if (userId != null) {
            return order.belongsToUser(userId);
        }

        // Анонимный пользователь может отменять только созданные заказы
        return order.getStatus() == OrderStatus.CREATED;
    }

    @GetMapping("/checkout")
    public String checkout(@AuthenticationPrincipal UserDetails userDetails,
                           Model model) {

        Long userId = null;
        User user = null;

        if (userDetails != null) {
            userId = userService.findByUsername(userDetails.getUsername())
                    .map(User::getId)
                    .orElse(null);
            user = userService.findById(userId).orElse(null);
        }

        // Получаем товары из корзины с учетом скидок
        List<CartItemDto> items;
        if (userId != null) {
            items = cartService.getUserCartItems(userId);
        } else {
            // Для анонимных пользователей
            Cart cart = cartService.getOrCreateCart(null, null);
            items = cartService.getCartItems(cart.getId());
        }

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
        model.addAttribute("isAuthenticated", userId != null);
        model.addAttribute("user", user);

        if (user != null) {
            model.addAttribute("userTypeDisplay", user.getUserType().getDisplayName());
        }

        return "order/checkout";
    }

    @PostMapping("/create")
    public String createOrder(@CookieValue(value = "sessionId") String sessionId,
                              @RequestParam(required = false) String email,
                              @RequestParam(required = false) String phone,
                              RedirectAttributes redirectAttributes,
                              Authentication authentication) {
        try {
            Long userId = getCurrentUserId(authentication);
            Cart cart = cartService.getOrCreateCart(userId, sessionId);

            // Проверяем, что корзина не пуста
            List<CartItemDto> items = cartService.getCartItems(cart.getId());
            if (items.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "Невозможно создать заказ из пустой корзины");
                return "redirect:/cart";
            }

            Order order;
            if (userId != null) {
                // Создаем заказ для зарегистрированного пользователя
                order = orderService.createOrderFromUserCart(userId);
            } else {
                // Для анонимного пользователя
                order = orderService.createOrder(cart.getId());

                // Если указаны контактные данные, сохраняем их в примечание
                if (StringUtils.hasText(email) || StringUtils.hasText(phone)) {
                    String note = String.format("Контактные данные: Email: %s, Телефон: %s",
                            email != null ? email : "не указан",
                            phone != null ? phone : "не указан");
                    orderService.addNote(order.getId(), note);
                }
            }

            redirectAttributes.addFlashAttribute("successMessage",
                    "Заказ №" + order.getOrderNumber() + " успешно создан!");

            return "redirect:/order/" + order.getId();

        } catch (IllegalStateException e) {
            log.warn("Попытка создать заказ из пустой корзины: sessionId={}", sessionId);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Невозможно создать заказ из пустой корзины");
            return "redirect:/cart";
        } catch (IllegalArgumentException e) {
            log.warn("Корзина не найдена: sessionId={}", sessionId, e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Корзина не найдена");
            return "redirect:/cart";
        } catch (Exception e) {
            log.error("Ошибка при создании заказа: sessionId={}", sessionId, e);
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
                if (!canCancelOrder(order, userId)) {
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
