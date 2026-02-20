package ru.fisher.ToolsMarket.controller.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.fisher.ToolsMarket.models.User;
import ru.fisher.ToolsMarket.models.UserDiscount;
import ru.fisher.ToolsMarket.models.UserType;
import ru.fisher.ToolsMarket.repository.UserDiscountRepository;
import ru.fisher.ToolsMarket.service.UserService;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserDiscountRepository discountRepository;
    private final UserService userService;

    @GetMapping
    public String listUsers(Model model,
                            @RequestParam(required = false) String search,
                            @RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "20") int size) {

        Page<User> usersPage;

        if (search != null && !search.trim().isEmpty()) {
            usersPage = userService.searchUsers(search.trim(), PageRequest.of(page, size));
        } else {
            usersPage = userService.getAllUsers(PageRequest.of(page, size));
        }

        // Получаем статистику по типам пользователей
        Map<UserType, Long> userTypeStats = userService.getUserTypeStatistics();

        model.addAttribute("users", usersPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", usersPage.getTotalPages());
        model.addAttribute("totalItems", usersPage.getTotalElements());
        model.addAttribute("search", search);
        model.addAttribute("userTypes", UserType.values());
        model.addAttribute("userTypeStats", userTypeStats);

        return "admin/users/index";
    }

    @GetMapping("/{id}")
    public String viewUser(@PathVariable Long id, Model model) {
        User user = userService.findByIdWithOrders(id)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        // Получаем скидки, доступные для типа пользователя
        List<UserDiscount> userDiscounts = discountRepository.findByUserType(user.getUserType());

        // Статистика пользователя
        int orderCount = user.getOrders() != null ? user.getOrders().size() : 0;

        model.addAttribute("user", user);
        model.addAttribute("userTypes", UserType.values());
        model.addAttribute("userDiscounts", userDiscounts);
        model.addAttribute("orderCount", orderCount);

        return "admin/users/view";
    }

    @GetMapping("/{id}/edit")
    public String editUserForm(@PathVariable Long id, Model model) {
        User user = userService.findByIdWithOrders(id)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        model.addAttribute("user", user);
        model.addAttribute("userTypes", UserType.values());

        return "admin/users/edit";
    }

    @PostMapping("/{id}/update")
    public String updateUser(@PathVariable Long id,
                             @RequestParam(required = false) String firstName,
                             @RequestParam(required = false) String lastName,
                             @RequestParam(required = false) String email,
                             @RequestParam(required = false) String phone,
                             @RequestParam(required = false) String userType,
                             @RequestParam(required = false) String note,
                             @RequestParam(defaultValue = "true") boolean enabled,
                             RedirectAttributes redirectAttributes) {

        try {
            UserType type = null;
            if (userType != null && !userType.isEmpty()) {
                type = UserType.valueOf(userType.toUpperCase());
            }

            userService.updateUser(id, firstName, lastName, email, phone, type, note, enabled);

            redirectAttributes.addFlashAttribute("success", "Пользователь обновлен");

        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error",
                    "Некорректные данные: " + e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "Ошибка обновления: " + e.getMessage());
        }

        return "redirect:/admin/users/" + id;
    }

    @PostMapping("/{id}/change-user-type")
    public String changeUserType(@PathVariable Long id,
                                 @RequestParam String userType,
                                 RedirectAttributes redirectAttributes) {

        try {
            UserType type = UserType.valueOf(userType.toUpperCase());
            userService.changeUserType(id, type);

            redirectAttributes.addFlashAttribute("success",
                    "Тип пользователя изменен на: " + type.getDisplayName());

        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error",
                    "Некорректный тип пользователя");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "Ошибка изменения типа: " + e.getMessage());
        }

        return "redirect:/admin/users/" + id;
    }

    @PostMapping("/{id}/toggle-status")
    public String toggleUserStatus(@PathVariable Long id,
                                   RedirectAttributes redirectAttributes) {

        try {
            userService.toggleUserStatus(id);

            User user = userService.findById(id).orElse(null);
            String status = user != null && user.isEnabled() ? "активирован" : "деактивирован";
            redirectAttributes.addFlashAttribute("success",
                    "Пользователь " + status);

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "Ошибка изменения статуса: " + e.getMessage());
        }

        return "redirect:/admin/users/" + id;
    }

    @PostMapping("/{id}/delete")
    public String deleteUser(@PathVariable Long id,
                             RedirectAttributes redirectAttributes) {

        try {
            userService.deleteUser(id);
            redirectAttributes.addFlashAttribute("success", "Пользователь удален");
            return "redirect:/admin/users";

        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error",
                    "Нельзя удалить пользователя: " + e.getMessage());
            return "redirect:/admin/users/" + id;

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "Ошибка удаления: " + e.getMessage());
            return "redirect:/admin/users/" + id;
        }
    }

    @GetMapping("/{id}/change-password")
    public String changePasswordForm(@PathVariable Long id, Model model) {
        User user = userService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        model.addAttribute("user", user);
        return "admin/users/change-password";
    }

    @PostMapping("/{id}/change-password")
    public String changePassword(@PathVariable Long id,
                                 @RequestParam String newPassword,
                                 @RequestParam String confirmPassword,
                                 RedirectAttributes redirectAttributes) {

        try {
            if (!newPassword.equals(confirmPassword)) {
                redirectAttributes.addFlashAttribute("error", "Пароли не совпадают");
                return "redirect:/admin/users/" + id + "/change-password";
            }

            if (newPassword.length() < 6) {
                redirectAttributes.addFlashAttribute("error", "Пароль должен быть не менее 6 символов");
                return "redirect:/admin/users/" + id + "/change-password";
            }

            // Используем существующий метод changePassword, но с фиктивным старым паролем
            // Или создадим отдельный метод для админской смены пароля
            userService.changePasswordByAdmin(id, newPassword);

            redirectAttributes.addFlashAttribute("success", "Пароль успешно изменен");
            return "redirect:/admin/users/" + id;

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Ошибка изменения пароля: " + e.getMessage());
            return "redirect:/admin/users/" + id + "/change-password";
        }
    }

}
