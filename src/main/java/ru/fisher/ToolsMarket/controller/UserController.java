package ru.fisher.ToolsMarket.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.fisher.ToolsMarket.dto.OrderItemDto;
import ru.fisher.ToolsMarket.dto.UserProfileUpdateDto;
import ru.fisher.ToolsMarket.models.Order;
import ru.fisher.ToolsMarket.models.OrderStatus;
import ru.fisher.ToolsMarket.models.User;
import ru.fisher.ToolsMarket.service.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

@Controller
@RequestMapping("/profile")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserService userService;
    private final OrderService orderService;
    private final DiscountService discountService;
    private final CartService cartService;
    private final ProductService productService;

    @GetMapping
    public String profilePage(Model model, Authentication authentication) {
        Long userId = getCurrentUserId(authentication);
        User user = userService.findById(userId).orElseThrow();
        List<Order> orders = orderService.getUserOrdersWithItems(userId);

        // Получаем активные заказы (не завершенные)
        List<Order> activeOrders = orders.stream()
                .filter(order -> order.getStatus() != OrderStatus.COMPLETED &&
                        order.getStatus() != OrderStatus.CANCELLED)
                .toList();

        // Получаем завершенные заказы (уже есть в orders, фильтруем)
        List<Order> completedOrders = orders.stream()
                .filter(order -> order.getStatus() == OrderStatus.COMPLETED)
                .toList();

        // Считаем общую сумму потраченных денег
        BigDecimal totalSpent = completedOrders.stream()
                .map(Order::getTotalPrice)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Проверяем роль и добавляем флаг
        boolean isAdmin = user.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"));
        model.addAttribute("isAdmin", isAdmin);

        model.addAttribute("user", user);
        model.addAttribute("orders", orders);
        model.addAttribute("orderCount", orders.size());
        model.addAttribute("activeOrderCount", activeOrders.size());
        model.addAttribute("totalSpent", totalSpent);

        return "profile/index";
    }

    @GetMapping("/orders")
    public String userOrders(Model model, Authentication authentication) {
        Long userId = getCurrentUserId(authentication);
        if (userId == null) {
            return "redirect:/auth/login";
        }
        List<Order> orders = orderService.getUserOrders(userId);
        model.addAttribute("orders", orders);
        return "profile/orders";
    }

    @GetMapping("/orders/{id}")
    public String orderDetail(@PathVariable Long id,
                              Model model,
                              Authentication authentication) {
        Long userId = getCurrentUserId(authentication);

        if (userId == null) {
            return "redirect:/auth/login";
        }

        Order order = orderService.getOrderWithProducts(id);

        if (!order.getUser().getId().equals(userId)) {
            return "redirect:/profile/orders";
        }

        // ИСПРАВЛЕНО: Используем метод БЕЗ DiscountService
        List<OrderItemDto> orderItemDtos = order.getOrderItems()
                .stream()
                .map(OrderItemDto::fromEntity) // ← Без discountService!
                .toList();

        // Рассчитываем итоги из сохраненных данных
        BigDecimal originalTotal = BigDecimal.ZERO;
        BigDecimal totalDiscount = BigDecimal.ZERO;

        for (OrderItemDto dto : orderItemDtos) {
            // Используем сохраненные данные
            BigDecimal itemOriginalTotal = dto.getOriginalPrice()
                    .multiply(BigDecimal.valueOf(dto.getQuantity()));
            originalTotal = originalTotal.add(itemOriginalTotal);

            if (dto.isHasDiscount() && dto.getDiscountAmount() != null) {
                totalDiscount = totalDiscount.add(dto.getDiscountAmount());
            }
        }

        // Если в Dto нет метода getTotalWithoutDiscount, добавим локальную переменную
        BigDecimal totalWithoutDiscount = orderItemDtos.stream()
                .map(dto -> dto.getOriginalPrice().multiply(
                        BigDecimal.valueOf(dto.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        boolean hasDiscounts = totalDiscount.compareTo(BigDecimal.ZERO) > 0;
        boolean canCancel = canCancelOrder(order, userId);

        model.addAttribute("order", order);
        model.addAttribute("orderItems", orderItemDtos);
        model.addAttribute("canCancel", canCancel);
        model.addAttribute("isAuthenticated", true);
        model.addAttribute("originalTotal", originalTotal);
        model.addAttribute("totalDiscount", totalDiscount);
        model.addAttribute("hasDiscounts", hasDiscounts);
        model.addAttribute("totalWithoutDiscount", totalWithoutDiscount); // Добавляем

        // Тип пользователя для отображения
        if (order.getUser().getUserType() != null) {
            model.addAttribute("userTypeDisplay", order.getUser().getUserType().getDisplayName());
        }

        return "profile/order-detail";
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

    @PostMapping("/orders/{id}/cancel")
    public String cancelOrder(@PathVariable Long id,
                              Authentication authentication,
                              RedirectAttributes redirectAttributes) {
        Long userId = getCurrentUserId(authentication);
        if (userId == null) {
            return "redirect:/auth/login";
        }

        try {
            orderService.cancelOrder(id, userId);
            redirectAttributes.addFlashAttribute("successMessage", "Заказ успешно отменен");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Ошибка при отмене заказа");
        }

        return "redirect:/profile/orders/" + id;
    }

    @GetMapping("/edit")
    public String editProfilePage(Model model,
                                  Authentication authentication) {
        Long userId = getCurrentUserId(authentication);
        User user = userService.findById(userId).orElseThrow();

        UserProfileUpdateDto userDto = new UserProfileUpdateDto();
        userDto.setEmail(user.getEmail());
        userDto.setFirstName(user.getFirstName());
        userDto.setLastName(user.getLastName());
        userDto.setPhone(user.getPhone());

        model.addAttribute("userDto", userDto);
        return "profile/edit";
    }

    @PostMapping("/update")
    public String updateProfile(
            @Valid @ModelAttribute("userDto") UserProfileUpdateDto userDto,
            BindingResult bindingResult,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        Long userId = getCurrentUserId(authentication);
        if (userId == null) {
            return "redirect:/auth/login";
        }

        // Дополнительная проверка на совпадение паролей
        if (userDto.isPasswordChangeRequested()) {
            if (!StringUtils.hasText(userDto.getNewPassword())) {
                bindingResult.rejectValue("newPassword", "newPassword.required",
                        "Введите новый пароль");
            } else if (!userDto.getNewPassword().equals(userDto.getConfirmPassword())) {
                bindingResult.rejectValue("confirmPassword", "confirmPassword.mismatch",
                        "Пароли не совпадают");
            }
        }

        // Если есть ошибки валидации - возвращаем на форму
        if (bindingResult.hasErrors()) {
            return "profile/edit";
        }

        try {
            userService.updateProfile(userId, userDto);

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    userDto.isPasswordChangeRequested()
                            ? "Профиль и пароль успешно обновлены"
                            : "Профиль успешно обновлен"
            );

            return "redirect:/profile";

        } catch (IllegalArgumentException e) {
            handleProfileUpdateErrors(e, bindingResult);
            return "profile/edit";
        }
    }

    private void handleProfileUpdateErrors(IllegalArgumentException e,
                                           BindingResult bindingResult) {
        String error = e.getMessage();

        if (error.contains("Email уже используется")) {
            bindingResult.rejectValue("email", "error.email", error);
        } else if (error.contains("Неверный текущий пароль")) {
            bindingResult.rejectValue("currentPassword", "error.currentPassword", error);
        } else if (error.contains("Новый пароль должен отличаться")) {
            bindingResult.rejectValue("newPassword", "error.newPassword", error);
        } else {
            bindingResult.reject("globalError", error);
        }
    }

    @GetMapping("/orders/status/{status}")  // Добавляем /status/
    public String userOrdersByStatus(@PathVariable String status,
                                     Model model,
                                     Authentication authentication) {
        Long userId = getCurrentUserId(authentication);
        if (userId == null) {
            return "redirect:/auth/login";
        }

        try {
            OrderStatus orderStatus = OrderStatus.valueOf(status.toUpperCase());
            List<Order> orders = orderService.getUserOrdersByStatus(userId, orderStatus);
            model.addAttribute("orders", orders);
            model.addAttribute("status", orderStatus);
            model.addAttribute("user", userService.findById(userId).orElse(null));

            return "profile/orders";

        } catch (IllegalArgumentException e) {
            return "redirect:/profile/orders";
        }
    }

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
