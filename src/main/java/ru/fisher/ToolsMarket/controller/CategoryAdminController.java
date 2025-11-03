package ru.fisher.ToolsMarket.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import ru.fisher.ToolsMarket.models.Category;
import ru.fisher.ToolsMarket.service.CategoryService;

import java.time.Instant;
import java.util.List;


@Controller
@RequestMapping("/admin/categories")
@RequiredArgsConstructor
@Slf4j
public class CategoryAdminController {

    private final CategoryService categoryService;

    // Список всех категорий
    @GetMapping
    public String index(Model model) {
        List<Category> categories = categoryService.findAllEntities();
        model.addAttribute("categories", categories);
        return "admin/categories/index";
    }

    // Просмотр категории
    @GetMapping("/{id}")
    public String show(@PathVariable Long id, Model model) {
        Category category = categoryService.findEntityById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
        model.addAttribute("category", category);
        return "admin/categories/show";
    }

    // Форма создания
    @GetMapping("/new")
    public String newCategory(Model model) {
        model.addAttribute("category", new Category());
        model.addAttribute("allCategories", categoryService.findAllEntities());
        return "admin/categories/new";
    }

    // Создание категории
    @PostMapping
    public String create(@ModelAttribute @Valid Category category,
                         BindingResult bindingResult, @RequestParam(required = false) Long parent,
                         Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("allCategories", categoryService.findAllEntities());
            return "admin/categories/new"; // Возвращаем форму с ошибками
        }

        // Устанавливаем родительскую категорию
        if (parent != null) {
            Category parentCategory = categoryService.findEntityById(parent)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parent category not found"));
            category.setParent(parentCategory);
        } else {
            category.setParent(null);
        }

        // Устанавливаем createdAt если не установлен
        if (category.getCreatedAt() == null) {
            category.setCreatedAt(Instant.now());
        }

        try {
            categoryService.saveEntity(category);
            return "redirect:/admin/categories"; // Редирект только при успехе
        } catch (DataIntegrityViolationException e) {
            bindingResult.rejectValue("title", "duplicate",
                    "Категория с таким URL уже существует");
            model.addAttribute("allCategories", categoryService.findAllEntities());
            return "admin/categories/new";
        }
    }

    // Форма редактирования
    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        Category category = categoryService.findEntityById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
        model.addAttribute("category", category);
        model.addAttribute("allCategories", categoryService.findAllEntities());
        return "admin/categories/edit";
    }

    // Обновление категории
    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @ModelAttribute @Valid Category category,
                         BindingResult bindingResult,
                         Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("allCategories", categoryService.findAllEntities());
            return "admin/categories/edit";
        }

        Category existing = categoryService.findEntityById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));

        // Обновляем поля
        existing.setName(category.getName());
        existing.setTitle(category.getTitle());
        existing.setDescription(category.getDescription());
        existing.setParent(category.getParent());
        existing.setSortOrder(category.getSortOrder());

        categoryService.saveEntity(existing);
        return "redirect:/admin/categories";
    }

    // Удаление категории
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        categoryService.deleteEntity(id);
        return "redirect:/admin/categories";
    }
}
