package ru.fisher.ToolsMarket.controller.admin;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import ru.fisher.ToolsMarket.models.Category;
import ru.fisher.ToolsMarket.service.CategoryService;
import ru.fisher.ToolsMarket.service.ImageStorageService;

import java.time.Instant;
import java.util.List;


@Controller
@RequestMapping("/admin/categories")
@RequiredArgsConstructor
@Slf4j
public class CategoryAdminController {

    private final CategoryService categoryService;
    private final ImageStorageService imageStorageService;

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

    // Создание категории с изображением
    @PostMapping
    public String create(@RequestParam(required = false) MultipartFile image,
                         @Valid @ModelAttribute Category category,
                         @RequestParam(required = false) Long parentId,
                         BindingResult bindingResult,
                         Model model) {

        log.info("Creating category: {}, title: {}", category.getName(), category.getTitle());

        // Проверьте ошибки валидации
        if (bindingResult.hasErrors()) {
            log.warn("Validation errors: {}", bindingResult.getAllErrors());
            model.addAttribute("allCategories", categoryService.findAllEntities());
            return "admin/categories/new";  // Верните форму с ошибками
        }

        // Создаем категорию
//        category.setName(name);
//        category.setTitle(title);
//        category.setDescription(description);
        category.setSortOrder(category.getSortOrder() != null ? category.getSortOrder() : 0);
        category.setCreatedAt(Instant.now());

        // Устанавливаем родителя
        if (parentId != null) {
            Category parent = categoryService.findEntityById(parentId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parent category not found"));
            category.setParent(parent);
        } else {
            category.setParent(null);
        }

        // Сохраняем изображение категории
        if (image != null && !image.isEmpty()) {
            log.info("Saving category image: {} ({} bytes)",
                    image.getOriginalFilename(), image.getSize());
            try {
                String imageUrl = imageStorageService.saveCategoryImage(image, category.getTitle());
                category.setImageUrl(imageUrl);

                // Генерируем миниатюру
                String thumbnailUrl = imageStorageService.generateThumbnail(imageUrl);
                category.setThumbnailUrl(thumbnailUrl);

                log.info("Category image saved: {}", imageUrl);
                log.info("Thumbnail generated: {}", thumbnailUrl);
            } catch (Exception e) {
                log.error("Failed to save category image: {}", e.getMessage(), e);
                // Можно добавить сообщение об ошибке в модель
                model.addAttribute("error", "Ошибка при сохранении изображения: " + e.getMessage());
                model.addAttribute("allCategories", categoryService.findAllEntities());
                return "admin/categories/new";
            }
        }

        try {
            categoryService.saveEntity(category);
            log.info("Category created with ID: {}", category.getId());
            return "redirect:/admin/categories";

        } catch (DataIntegrityViolationException e) {
            log.error("Data integrity violation: {}", e.getMessage());
            model.addAttribute("error", "Категория с таким URL уже существует");
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

    // Обновление категории с изображением
    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @RequestParam String name,
                         @RequestParam String title,
                         @RequestParam(required = false) String description,
                         @RequestParam(required = false) Integer sortOrder,
                         @RequestParam(required = false) Long parentId,
                         @RequestParam(required = false) MultipartFile newImage,
                         @RequestParam(defaultValue = "false") boolean deleteImage,
                         Model model) {

        log.info("Updating category ID: {}", id);

        Category existing = categoryService.findEntityById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));

        // Сохраняем старые значения изображений
        String oldImageUrl = existing.getImageUrl();
        String oldThumbnailUrl = existing.getThumbnailUrl();

        // Обновляем основные поля
        existing.setName(name);
        existing.setTitle(title);
        existing.setDescription(description);
        existing.setSortOrder(sortOrder != null ? sortOrder : 0);

        // Обновляем родителя
        if (parentId != null) {
            Category parent = categoryService.findEntityById(parentId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parent category not found"));
            existing.setParent(parent);
        } else {
            existing.setParent(null);
        }

        // Удаляем существующее изображение если нужно
        if (deleteImage && oldImageUrl != null) {
            log.info("Deleting category image: {}", oldImageUrl);
            imageStorageService.deleteCategoryImage(oldImageUrl);
            existing.setImageUrl(null);
            existing.setThumbnailUrl(null);
        }

        // Добавляем новое изображение
        if (newImage != null && !newImage.isEmpty()) {
            log.info("Adding new category image: {} ({} bytes)",
                    newImage.getOriginalFilename(), newImage.getSize());

            // Сначала удаляем старое изображение если есть
            if (oldImageUrl != null && !deleteImage) {
                imageStorageService.deleteCategoryImage(oldImageUrl);
            }

            try {
                String imageUrl = imageStorageService.saveCategoryImage(newImage, title);
                existing.setImageUrl(imageUrl);

                String thumbnailUrl = imageStorageService.generateThumbnail(imageUrl);
                existing.setThumbnailUrl(thumbnailUrl);

                log.info("New category image saved: {}", imageUrl);
            } catch (Exception e) {
                log.error("Failed to save new category image: {}", e.getMessage(), e);
                model.addAttribute("error", "Ошибка при сохранении изображения: " + e.getMessage());
                model.addAttribute("allCategories", categoryService.findAllEntities());
                return "admin/categories/edit";
            }
        }

        try {
            Category savedCategory = categoryService.saveEntity(existing);
            log.info("Category updated. Image URL: {}, Thumbnail URL: {}",
                    savedCategory.getImageUrl(), savedCategory.getThumbnailUrl());
            return "redirect:/admin/categories";

        } catch (DataIntegrityViolationException e) {
            log.error("Data integrity violation: {}", e.getMessage());
            model.addAttribute("error", "Категория с таким URL уже существует");
            model.addAttribute("allCategories", categoryService.findAllEntities());
            return "admin/categories/edit";
        }
    }

    // Удаление категории
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        Category category = categoryService.findEntityById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));

        // Удаляем изображения категории если есть
        if (category.getImageUrl() != null) {
            imageStorageService.deleteCategoryImage(category.getImageUrl());
        }

        categoryService.deleteEntity(id);
        return "redirect:/admin/categories";
    }
}
