package ru.fisher.ToolsMarket.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import ru.fisher.ToolsMarket.models.User;
import ru.fisher.ToolsMarket.service.CategoryService;
import ru.fisher.ToolsMarket.service.UserService;

import java.security.Principal;
import java.util.Optional;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final CategoryService categoryService;
    private final UserService userService;

    @GetMapping("/")
    public String home(Model model, Principal principal) {
        // Получаем ВСЕ категории для главной страницы
        model.addAttribute("allCategories", categoryService.findAll());

        // И корневые категории для меню навигации
        model.addAttribute("categories", categoryService.getRootCategories());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Optional<User> user = userService.findByUsername(auth.getName());
        model.addAttribute("user", user);

        // Проверка авторизации
        boolean isAuthenticated = principal != null;
        model.addAttribute("isAuthenticated", isAuthenticated);

        // Если пользователь авторизован, добавляем данные
        if (isAuthenticated) {
            String username = principal.getName();
            model.addAttribute("currentUser", userService.findByUsername(username).orElse(null));
        }

        return "index";
    }
}
