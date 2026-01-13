package ru.fisher.ToolsMarket.controller.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.fisher.ToolsMarket.models.ProductType;
import ru.fisher.ToolsMarket.models.UserDiscount;
import ru.fisher.ToolsMarket.models.UserType;
import ru.fisher.ToolsMarket.repository.UserDiscountRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/admin/discounts")  // Обратите внимание на другой путь!
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminDiscountController {

    private final UserDiscountRepository discountRepository;


    @GetMapping
    public String discountSettings(Model model) {
        List<UserDiscount> discounts = discountRepository.findAll();

        discounts.sort(Comparator
                .comparing(UserDiscount::getUserType)
                .thenComparing(UserDiscount::getProductType));

        model.addAttribute("discounts", discounts);
        model.addAttribute("userTypes", UserType.values());
        model.addAttribute("productTypes", ProductType.values());

        return "admin/discounts/index";
    }

    @PostMapping("/save")
    public String saveDiscount(
            @RequestParam(required = false) Long id,
            @RequestParam(required = false) String userType,
            @RequestParam(required = false) String productType,
            @RequestParam(required = false) BigDecimal discountPercentage,
            @RequestParam(defaultValue = "false") boolean active,
            RedirectAttributes redirectAttributes) {

        try {
            // Проверяем обязательные поля
            if (userType == null || userType.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Тип пользователя обязателен");
                return "redirect:/admin/discounts";
            }
            if (productType == null || productType.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Тип товара обязателен");
                return "redirect:/admin/discounts";
            }
            if (discountPercentage == null) {
                redirectAttributes.addFlashAttribute("error", "Процент скидки обязателен");
                return "redirect:/admin/discounts";
            }

            UserDiscount discount;

            if (id != null) {
                // Редактирование существующей скидки
                discount = discountRepository.findById(id)
                        .orElseThrow(() -> new IllegalArgumentException("Скидка не найдена"));
            } else {
                // Создание новой скидки
                discount = new UserDiscount();
                discount.setCreatedAt(Instant.now());
            }

            // Конвертируем строки в enum'ы
            discount.setUserType(UserType.valueOf(userType.toUpperCase()));
            discount.setProductType(ProductType.valueOf(productType.toUpperCase()));
            discount.setDiscountPercentage(discountPercentage);
            discount.setActive(active);
            discount.setUpdatedAt(Instant.now());

            // Проверяем уникальность комбинации
            Optional<UserDiscount> existing = discountRepository
                    .findByUserTypeAndProductType(discount.getUserType(), discount.getProductType());

            if (existing.isPresent() && !existing.get().getId().equals(discount.getId())) {
                redirectAttributes.addFlashAttribute("error",
                        "Скидка для этой комбинации уже существует");
                return "redirect:/admin/discounts";
            }

            discountRepository.save(discount);

            String message = id != null ? "Скидка обновлена" : "Скидка сохранена";
            redirectAttributes.addFlashAttribute("success", message);

        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error",
                    "Некорректные данные: " + e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "Ошибка сохранения: " + e.getMessage());
        }
        return "redirect:/admin/discounts";
    }

    @PostMapping("/delete/{id}")
    public String deleteDiscount(@PathVariable Long id,
                                 RedirectAttributes redirectAttributes) {
        try {
            discountRepository.deleteById(id);
            redirectAttributes.addFlashAttribute("success", "Скидка удалена");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Ошибка удаления");
        }
        return "redirect:/admin/discounts";
    }

    @GetMapping("/edit/{id}")
    public String editDiscount(@PathVariable Long id, Model model) {
        UserDiscount discount = discountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Скидка не найдена"));

        List<UserDiscount> discounts = discountRepository.findAll();
        discounts.sort(Comparator
                .comparing(UserDiscount::getUserType)
                .thenComparing(UserDiscount::getProductType));

        model.addAttribute("discounts", discounts);
        model.addAttribute("userTypes", UserType.values());
        model.addAttribute("productTypes", ProductType.values());
        model.addAttribute("discount", discount);

        return "admin/discounts/index";
    }
}
