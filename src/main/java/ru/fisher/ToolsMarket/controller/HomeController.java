package ru.fisher.ToolsMarket.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import ru.fisher.ToolsMarket.service.CategoryService;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final CategoryService categoryService;

    @GetMapping("/")
    public String home(Model model) {
        // Получаем ВСЕ категории для главной страницы
        model.addAttribute("allCategories", categoryService.findAll());

        // И корневые категории для меню навигации
        model.addAttribute("categories", categoryService.getRootCategories());

        return "index";
    }
}
