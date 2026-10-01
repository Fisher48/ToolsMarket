package ru.fisher.ToolsMarket.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import ru.fisher.ToolsMarket.dto.CategoryDTO.CategoryDto;
import ru.fisher.ToolsMarket.models.User;
import ru.fisher.ToolsMarket.service.CategoryService;
import ru.fisher.ToolsMarket.service.CurrentUserService;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final CategoryService categoryService;
    private final CurrentUserService currentUserService;

    @GetMapping("/")
    public String home(Model model, Principal principal) {
        // Используем метод для родительских категорий
        List<CategoryDto> parentCategories = categoryService.getParentCategoriesForHome();

        model.addAttribute("allCategories", parentCategories);
        model.addAttribute("categories", parentCategories);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Optional<User> user = currentUserService.findCurrentUser(auth);
        model.addAttribute("user", user);

        boolean isAuthenticated = principal != null;
        model.addAttribute("isAuthenticated", isAuthenticated);

        if (isAuthenticated) {
            model.addAttribute("currentUser", currentUserService.findCurrentUser(auth).orElse(null));
        }

        return "index";
    }
}
