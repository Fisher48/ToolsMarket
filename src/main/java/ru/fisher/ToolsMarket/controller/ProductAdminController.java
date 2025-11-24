package ru.fisher.ToolsMarket.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import ru.fisher.ToolsMarket.models.*;
import ru.fisher.ToolsMarket.service.AttributeService;
import ru.fisher.ToolsMarket.service.CategoryService;
import ru.fisher.ToolsMarket.service.ImageStorageService;
import ru.fisher.ToolsMarket.service.ProductService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@Slf4j
@RequestMapping("/admin/products")
@RequiredArgsConstructor
public class ProductAdminController {

    private final ProductService productService;
    private final CategoryService categoryService;
    private final ImageStorageService imageStorageService;
    private final AttributeService attributeService;

    // Список
    @GetMapping
    public String index(Model model) {
        model.addAttribute("products", productService.findAllEntities());
        return "admin/products/index";
    }

    // Просмотр
    @GetMapping("/{id}")
    public String show(@PathVariable Long id, Model model) {
        Product product = productService.findEntityById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));

        // Загружаем значения атрибутов
        Map<Attribute, String> productAttributes = attributeService.getProductAttributes(product);
        model.addAttribute("product", product);
        model.addAttribute("productAttributes", productAttributes);

        return "admin/products/show";
    }

    // Создание — форма
    @GetMapping("/new")
    public String newProduct(Model model) {
        model.addAttribute("product", new Product());
        model.addAttribute("categories", categoryService.findAllCategories());
        return "admin/products/new";
    }

    // Сохранение
//    @PostMapping
//    public String create(@ModelAttribute @Valid Product product, BindingResult bindingResult, Model model,
//                         @RequestParam(required = false) List<Long> categoryIds) {
//        if (bindingResult.hasErrors()) {
//            return "admin/products/new"; // Возвращаем форму с ошибками
//        }
//
//        if (categoryIds != null && !categoryIds.isEmpty()) {
//            Set<Category> categories = new HashSet<>(categoryService.findByIds(categoryIds));
//            product.setCategories(categories);
//        }
//        productService.saveEntity(product);
//        return "redirect:/admin/products";
//    }

    // Обновляем метод создания товара
    @PostMapping
    public String create(@RequestParam String name,
                         @RequestParam String title,
                         @RequestParam(required = false) String shortDescription,
                         @RequestParam(required = false) String description,
                         @RequestParam(required = false) String sku,
                         @RequestParam BigDecimal price,
                         @RequestParam String currency,
                         @RequestParam(defaultValue = "true") boolean active,
                         @RequestParam(required = false) List<Long> categoryIds,
                         @RequestParam(required = false) List<MultipartFile> images,
                         @RequestParam(required = false) List<String> imageAlts,
                         @RequestParam(required = false) List<Integer> imageSortOrders) {

        log.info("Creating product: {}", name);
        log.info("Received {} images", images != null ? images.size() : 0);

        // Создаем новый продукт с установкой временных меток
        Product product = Product.builder()
                .name(name)
                .title(title)
                .shortDescription(shortDescription)
                .description(description)
                .sku(sku)
                .price(price)
                .currency(currency)
                .active(active)
                .createdAt(Instant.now())  // Устанавливаем createdAt
                .updatedAt(Instant.now())  // Устанавливаем updatedAt
                .categories(new HashSet<>())
                .images(new ArrayList<>())
                .build();

        // Устанавливаем категории
        if (categoryIds != null && !categoryIds.isEmpty()) {
            Set<Category> categories = new HashSet<>(categoryService.findByIds(categoryIds));
            product.setCategories(categories);
        }

        // Сохраняем товар, чтобы получить ID
        Product savedProduct = productService.saveEntity(product);
        log.info("Product saved with ID: {}", savedProduct.getId());

        // Сохраняем изображения
        if (images != null && !images.isEmpty()) {
            log.info("Saving {} images", images.size());
            saveProductImages(savedProduct, images, imageAlts, imageSortOrders);
            productService.saveEntity(savedProduct);
            log.info("Images saved successfully");
        }

        return "redirect:/admin/products";
    }

    // Редактирование — форма
    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        // Используем метод с полной загрузкой
        Product product = productService.findWithDetailsById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));

        // Получаем текущие значения характеристик
        Map<Long, String> currentValues = product.getAttributeValues().stream()
                .collect(Collectors.toMap(
                        av -> av.getAttribute().getId(),
                        ProductAttributeValue::getValue
                ));

        // Отладочный вывод
        System.out.println("=== DEBUG EDIT PRODUCT ===");
        System.out.println("Product ID: " + product.getId());
        System.out.println("Attribute values count: " + product.getAttributeValues().size());
        System.out.println("CurrentValues map: " + currentValues);
        product.getAttributeValues().forEach(av ->
                System.out.println("Attr: " + av.getAttribute().getName() + " = " + av.getValue())
        );

        model.addAttribute("product", product);
        model.addAttribute("categories", categoryService.findAllCategories());
        model.addAttribute("currentValues", currentValues);

//        model.addAttribute("product", product);
//        model.addAttribute("categories", categoryService.findAllCategories());
        return "admin/products/edit";
    }

    // Обновление
//    @PostMapping("/{id}")
//    public String update(@PathVariable Long id,
//                         @ModelAttribute @Valid Product product,
//                         @RequestParam(required = false) List<Long> categoryIds) {
//        Product existing = productService.findEntityById(id)
//                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
//
//        existing.setName(product.getName());
//        existing.setTitle(product.getTitle());
//        existing.setShortDescription(product.getShortDescription());
//        existing.setDescription(product.getDescription());
//        existing.setSku(product.getSku());
//        existing.setPrice(product.getPrice());
//        existing.setCurrency(product.getCurrency());
//        existing.setActive(product.isActive());
//
//        if (categoryIds != null) {
//            Set<Category> categories = new HashSet<>(categoryService.findByIds(categoryIds));
//            existing.setCategories(categories);
//        }
//
//        productService.saveEntity(existing);
//        return "redirect:/admin/products";
//    }

    // Обновляем метод редактирования товара
    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @RequestParam String name,
                         @RequestParam String title,
                         @RequestParam(required = false) String shortDescription,
                         @RequestParam(required = false) String description,
                         @RequestParam(required = false) String sku,
                         @RequestParam BigDecimal price,
                         @RequestParam String currency,
                         @RequestParam boolean active,
                         @RequestParam(required = false) List<Long> categoryIds,
                         @RequestParam(required = false) List<MultipartFile> newImages,
                         @RequestParam(required = false) List<String> newImageAlts,
                         @RequestParam(required = false) List<Integer> newImageSortOrders,
                         @RequestParam(required = false) List<Long> deleteImageIds,
                         @RequestParam(required = false) Map<String, String> allParams) {

        log.info("=== UPDATE PRODUCT START ===");
        log.info("Product ID: {}", id);
        log.info("New images count: {}", newImages != null ? newImages.size() : 0);
        log.info("Delete image IDs: {}", deleteImageIds);

        Product existing = productService.findEntityById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));

        // Обновляем основные поля
        updateProductFields(existing, name, title, shortDescription, description, sku, price, currency, active);

        // Обновляем категории
        if (categoryIds != null) {
            Set<Category> categories = new HashSet<>(categoryService.findByIds(categoryIds));
            existing.setCategories(categories);
        }

        // Обрабатываем характеристики
        if (allParams != null) {
            Map<Long, String> attributeValues = allParams.entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith("attr_"))
                    .collect(Collectors.toMap(
                            entry -> Long.parseLong(entry.getKey().substring(5)),
                            Map.Entry::getValue
                    ));

            if (!attributeValues.isEmpty()) {
                attributeService.saveProductAttributes(existing, attributeValues);
            }
        }

        // Удаляем отмеченные изображения
        if (deleteImageIds != null && !deleteImageIds.isEmpty()) {
            deleteProductImages(existing, deleteImageIds);
        }

        // Добавляем новые изображения
        if (newImages != null && !newImages.isEmpty()) {
            log.info("Saving {} new images", newImages.size());
            saveProductImages(existing, newImages, newImageAlts, newImageSortOrders);
        }

        existing.setUpdatedAt(Instant.now());
        productService.saveEntity(existing);

        log.info("=== UPDATE PRODUCT END ===");
        return "redirect:/admin/products";
    }

    private void updateProductFields(Product product, String name, String title,
                                     String shortDescription, String description, String sku,
                                     BigDecimal price, String currency, boolean active) {
        product.setName(name);
        product.setTitle(title);
        product.setShortDescription(shortDescription);
        product.setDescription(description);
        product.setSku(sku);
        product.setPrice(price);
        product.setCurrency(currency);
        product.setActive(active);
    }

    // Удаление
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        productService.deleteEntity(id);
        return "redirect:/admin/products";
    }

    private void saveProductImages(Product product, List<MultipartFile> images,
                                   List<String> alts, List<Integer> sortOrders) {
        log.info("Starting to save {} images for product: {}", images.size(), product.getTitle());

        for (int i = 0; i < images.size(); i++) {
            MultipartFile file = images.get(i);
            log.info("Processing image {}: {} (size: {})",
                    i, file.getOriginalFilename(), file.getSize());

            if (!file.isEmpty() && imageStorageService.isImage(file)) {
                try {
                    ProductImage productImage = imageStorageService.saveImage(file, product.getTitle());
                    log.info("Image saved: {}", productImage.getUrl());

                    // Устанавливаем дополнительные параметры
                    if (alts != null && i < alts.size() && alts.get(i) != null && !alts.get(i).isEmpty()) {
                        productImage.setAlt(alts.get(i));
                    }

                    if (sortOrders != null && i < sortOrders.size()) {
                        productImage.setSortOrder(sortOrders.get(i));
                    }

                    productImage.setProduct(product);
                    product.getImages().add(productImage);
                    log.info("ProductImage entity created and added to product");

                } catch (Exception e) {
                    log.error("Error saving image {}: {}", file.getOriginalFilename(), e.getMessage(), e);
                }
            } else {
                log.warn("Image {} is empty or not an image", file.getOriginalFilename());
            }
        }
    }

    private void deleteProductImages(Product product, List<Long> imageIds) {
        List<ProductImage> imagesToRemove = product.getImages().stream()
                .filter(image -> imageIds.contains(image.getId()))
                .toList();

        for (ProductImage image : imagesToRemove) {
            imageStorageService.deleteImage(image.getUrl());
            product.getImages().remove(image);
        }
    }

    @GetMapping("/{id}/specifications")
    public String specificationsForm(@PathVariable Long id, Model model) {
        Product product = productService.findWithDetailsById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        // Получаем атрибуты всех категорий товара
        Set<Attribute> allAttributes = product.getCategories().stream()
                .flatMap(category -> category.getAttributes().stream())
                .collect(Collectors.toSet());

        // Получаем текущие значения как Map<Long, String>
        Map<Long, String> currentValues = product.getAttributeValues().stream()
                .collect(Collectors.toMap(
                        av -> av.getAttribute().getId(),
                        ProductAttributeValue::getValue
                ));

        model.addAttribute("product", product);
        model.addAttribute("attributes", allAttributes);
        model.addAttribute("currentValues", currentValues); // Это Map<Long, String>

        return "admin/products/specifications";
    }

    @PostMapping("/{id}/specifications")
    public String saveSpecifications(@PathVariable Long id,
                                     @RequestParam Map<String, String> allParams) {
        Product product = productService.findEntityById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        // Фильтруем только параметры атрибутов
        Map<Long, String> attributeValues = allParams.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("attr_"))
                .collect(Collectors.toMap(
                        entry -> Long.parseLong(entry.getKey().substring(5)),
                        Map.Entry::getValue
                ));

        attributeService.saveProductAttributes(product, attributeValues);
        return "redirect:/admin/products/" + id;
    }


}
