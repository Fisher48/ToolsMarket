package ru.fisher.ToolsMarket.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import ru.fisher.ToolsMarket.dto.CategoryDto;
import ru.fisher.ToolsMarket.models.User;
import ru.fisher.ToolsMarket.service.CategoryService;
import ru.fisher.ToolsMarket.service.UserService;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final CategoryService categoryService;
    private final UserService userService;

    @GetMapping("/")
    public String home(Model model, Principal principal) {
        // Используем метод для родительских категорий
        List<CategoryDto> parentCategories = categoryService.getParentCategoriesForHome();

        model.addAttribute("allCategories", parentCategories);
        model.addAttribute("categories", parentCategories);  // Для меню тоже

        // Остальной код без изменений...
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Optional<User> user = userService.findByUsername(auth.getName());
        model.addAttribute("user", user);

        boolean isAuthenticated = principal != null;
        model.addAttribute("isAuthenticated", isAuthenticated);

        if (isAuthenticated) {
            String username = principal.getName();
            model.addAttribute("currentUser", userService.findByUsername(username).orElse(null));
        }

        return "index";
    }
}
