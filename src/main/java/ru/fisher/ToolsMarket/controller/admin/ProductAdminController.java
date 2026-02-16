package ru.fisher.ToolsMarket.controller.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.fisher.ToolsMarket.dto.ImageOrderDto;
import ru.fisher.ToolsMarket.dto.ProductAdminDto;
import ru.fisher.ToolsMarket.exceptions.ValidationException;
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
    public String index(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id,asc") String sort,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String sku,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            Model model) {

        String[] sortParams = sort.split(",");
        Sort.Direction direction = Sort.Direction.fromString(sortParams[1]);
        Sort sorting = Sort.by(direction, sortParams[0]);

        Pageable pageable = PageRequest.of(page, size, sorting);

        Page<ProductAdminDto> productPage = productService.search(
                name, sku, categoryId, active, minPrice, maxPrice, pageable);

        // Добавляем категории для выпадающего списка
        model.addAttribute("categories", categoryService.findAllCategories());
        model.addAttribute("productPage", productPage);
        model.addAttribute("currentSort", sort);

        return "admin/products/index";
    }

    // Просмотр
    @GetMapping("/{id}")
    public String show(@PathVariable Long id, Model model) {
        Product product = productService.findByIdWithAllRelations(id)
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
        model.addAttribute("allProductTypes", ProductType.values());
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
                         @RequestParam(required = false) List<Integer> imageSortOrders,
                         @RequestParam(required = false) ProductType productType) {

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
                .images(new HashSet<>())
                .productType(productType)
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
        Product product = productService.findByIdWithAllRelations(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));

        // Получаем ID категорий товара
        Set<Long> productCategoryIds = product.getCategories().stream()
                .map(Category::getId)
                .collect(Collectors.toSet());

        // Получаем текущие значения характеристик
        Map<Long, String> currentValues = product.getAttributeValues().stream()
                .collect(Collectors.toMap(
                        av -> av.getAttribute().getId(),
                        ProductAttributeValue::getValue)
                );

        // Отладочный вывод
        log.debug("=== DEBUG EDIT PRODUCT ===");
        log.debug("Product ID: " + product.getId());
        log.debug("Product categories count: " + product.getCategories().size());
        log.debug("Product category IDs: " + productCategoryIds);
        log.debug("Attribute values count: " + product.getAttributeValues().size());
        log.debug("CurrentValues map: " + currentValues);
        product.getAttributeValues().forEach(av ->
                log.debug("Attr: " + av.getAttribute().getName() + " = " + av.getValue())
        );

        model.addAttribute("product", product);
        model.addAttribute("categories", categoryService.findAllCategories());
        model.addAttribute("productCategoryIds", productCategoryIds);
        model.addAttribute("currentValues", currentValues);
        model.addAttribute("allProductTypes", ProductType.values());

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
//    @PostMapping("/{id}")
//    public String update(
//            @PathVariable Long id,
//            @RequestParam String name,
//            @RequestParam String title,
//            @RequestParam(required = false) String shortDescription,
//            @RequestParam(required = false) String description,
//            @RequestParam(required = false) String sku,
//            @RequestParam BigDecimal price,
//            @RequestParam String currency,
//            @RequestParam(defaultValue = "true") boolean active,
//            @RequestParam(required = false) List<Long> categoryIds,
//
//            // Параметры для изображений
//            @RequestParam(required = false) List<MultipartFile> newImages,
//            @RequestParam(required = false) List<String> newImageAlts,
//            @RequestParam(required = false) List<Integer> newImageOrders,
//            @RequestParam(required = false) List<Long> deleteImageIds,
//
//            // Все остальные параметры для alt и order
//            @RequestParam Map<String, String> allParams,
//
//            RedirectAttributes redirectAttributes) {
//
//        try {
//            log.info("=== ОБНОВЛЕНИЕ ТОВАРА {} ===", id);
//
//            // 1. Находим товар
//            Product product = productService.findWithDetailsById(id)
//                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Товар не найден"));
//
//            // 2. Обновляем основные поля
//            product.setName(name);
//            product.setTitle(title);
//            product.setShortDescription(shortDescription);
//            product.setDescription(description);
//            product.setSku(sku);
//            product.setPrice(price);
//            product.setCurrency(currency);
//            product.setActive(active);
//            product.setUpdatedAt(Instant.now());
//
//            // 3. Обновляем категории
//            if (categoryIds != null && !categoryIds.isEmpty()) {
//                Set<Category> categories = new HashSet<>(categoryService.findByIds(categoryIds));
//                product.setCategories(categories);
//            }
//
//            // 4. ОБРАБОТКА СУЩЕСТВУЮЩИХ ИЗОБРАЖЕНИЙ
//            if (product.getImages() != null) {
//                // 4.1. Обновляем alt и order для существующих изображений
//                for (ProductImage image : product.getImages()) {
//                    String altKey = "imageAlt_" + image.getId();
//                    String orderKey = "imageOrder_" + image.getId();
//
//                    if (allParams.containsKey(altKey)) {
//                        image.setAlt(allParams.get(altKey));
//                    }
//
//                    if (allParams.containsKey(orderKey)) {
//                        try {
//                            int order = Integer.parseInt(allParams.get(orderKey));
//                            image.setSortOrder(Math.max(1, order)); // Минимум 1
//                        } catch (NumberFormatException e) {
//                            log.warn("Неверный формат порядка для изображения {}: {}",
//                                    image.getId(), allParams.get(orderKey));
//                        }
//                    }
//                }
//
//                // 4.2. Удаляем отмеченные изображения
//                if (deleteImageIds != null && !deleteImageIds.isEmpty()) {
//                    removeImages(product, deleteImageIds);
//                }
//            }
//
//            // 5. ДОБАВЛЕНИЕ НОВЫХ ИЗОБРАЖЕНИЙ
//            if (newImages != null && !newImages.isEmpty()) {
//                log.info("Добавление {} новых изображений", newImages.size());
//                addNewImages(product, newImages, newImageAlts, newImageOrders);
//            }
//
//            // 6. Сохраняем товар (изображения сохранятся каскадно)
//            productService.saveEntity(product);
//
//            log.info("=== ТОВАР УСПЕШНО ОБНОВЛЕН ===");
//            redirectAttributes.addFlashAttribute("successMessage", "Товар успешно обновлен");
//
//        } catch (Exception e) {
//            log.error("Ошибка при обновлении товара", e);
//            redirectAttributes.addFlashAttribute("errorMessage",
//                    "Ошибка при обновлении товара: " + e.getMessage());
//            return "redirect:/admin/products/" + id + "/edit";
//        }
//
//        return "redirect:/admin/products/" + id;
//    }

    // ============ ОБНОВЛЕНИЕ ОСНОВНОЙ ИНФОРМАЦИИ (БЕЗ MULTIPART) ============
    @PostMapping("/{id}/update-info")
    public String updateProductInfo(
            @PathVariable Long id,
            @RequestParam String name,
            @RequestParam String title,
            @RequestParam(required = false) String shortDescription,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String sku,
            @RequestParam BigDecimal price,
            @RequestParam String currency,
            @RequestParam(defaultValue = "true") boolean active,
            @RequestParam(required = false) List<Long> categoryIds,
            @RequestParam(required = false) ProductType productType,
            RedirectAttributes redirectAttributes) {

        try {
            log.info("=== ОБНОВЛЕНИЕ ОСНОВНОЙ ИНФОРМАЦИИ ТОВАРА {} ===", id);

            Product product = productService.findByIdWithAllRelations(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Товар не найден"));

            // Обновляем основные поля
            product.setName(name);
            product.setTitle(title);
            product.setShortDescription(shortDescription);
            product.setDescription(description);
            product.setSku(sku);
            product.setPrice(price);
            product.setCurrency(currency);
            product.setActive(active);
            product.setProductType(productType);
            product.setUpdatedAt(Instant.now());

            // Обновляем категории
            Set<Category> newCategories = new HashSet<>(categoryService.findByIds(categoryIds));
            product.getCategories().clear();
            product.getCategories().addAll(newCategories);

            productService.saveEntity(product);

            redirectAttributes.addFlashAttribute("message", "Основная информация товара успешно обновлена");

        } catch (Exception e) {
            log.error("Ошибка при обновлении информации товара", e);
            redirectAttributes.addFlashAttribute("error", "Ошибка: " + e.getMessage());
        }

        return "redirect:/admin/products/" + id + "#main-info";
    }

    // ============ ОБНОВЛЕНИЕ ИЗОБРАЖЕНИЙ (С MULTIPART) ============
    @PostMapping("/{id}/update-images")
    public String updateProductImages(
            @PathVariable Long id,

            // Новые изображения
            @RequestParam(required = false) List<MultipartFile> newImages,
            @RequestParam(required = false) List<String> newImageAlts,
            @RequestParam(required = false) List<Integer> newImageOrders,

            // Существующие изображения (alt и order)
            @RequestParam(required = false) Map<String, String> allParams,

            // ID для удаления
            @RequestParam(required = false) List<Long> deleteImageIds,

            RedirectAttributes redirectAttributes) {

        try {
            log.info("=== ОБНОВЛЕНИЕ ИЗОБРАЖЕНИЙ ТОВАРА {} ===", id);
            log.info("Новых изображений: {}", newImages != null ? newImages.size() : 0);
            log.info("Удаление изображений: {}", deleteImageIds);

            Product product = productService.findByIdWithAllRelations(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Товар не найден"));

            // 1. УДАЛЯЕМ ОТМЕЧЕННЫЕ ИЗОБРАЖЕНИЯ
            if (deleteImageIds != null && !deleteImageIds.isEmpty()) {
                removeImages(product, deleteImageIds);
            }

            // 2. ОБНОВЛЯЕМ ALT И ORDER СУЩЕСТВУЮЩИХ ИЗОБРАЖЕНИЙ
            if (allParams != null && product.getImages() != null) {
                for (ProductImage image : product.getImages()) {
                    // Обновляем alt
                    String altKey = "imageAlt_" + image.getId();
                    if (allParams.containsKey(altKey)) {
                        String newAlt = allParams.get(altKey);
                        image.setAlt(newAlt);
                        log.info("Изображение {}: alt обновлен на '{}'", image.getId(), newAlt);
                    }

                    // Обновляем order
                    String orderKey = "imageOrder_" + image.getId();
                    if (allParams.containsKey(orderKey)) {
                        try {
                            int newOrder = Integer.parseInt(allParams.get(orderKey));
                            image.setSortOrder(newOrder);
                            log.info("Изображение {}: order обновлен на {}", image.getId(), newOrder);
                        } catch (NumberFormatException e) {
                            log.warn("Неверный формат order для изображения {}: {}",
                                    image.getId(), allParams.get(orderKey));
                        }
                    }
                }
            }

            // 3. ДОБАВЛЯЕМ НОВЫЕ ИЗОБРАЖЕНИЯ
            if (newImages != null && !newImages.isEmpty()) {
                addNewImages(product, newImages, newImageAlts, newImageOrders);
            }

            // Сохраняем товар (изменения в изображениях сохранятся каскадно)
            productService.saveEntity(product);

            redirectAttributes.addFlashAttribute("message",
                    String.format("Изображения обновлены. Добавлено: %d, Удалено: %d",
                            newImages != null ? newImages.stream().filter(f -> !f.isEmpty()).count() : 0,
                            deleteImageIds != null ? deleteImageIds.size() : 0));

        } catch (Exception e) {
            log.error("Ошибка при обновлении изображений товара", e);
            redirectAttributes.addFlashAttribute("error", "Ошибка при обновлении изображений: " + e.getMessage());
        }

        return "redirect:/admin/products/" + id + "#images";
    }

    @PostMapping("/{id}/images/order")
    @ResponseBody
    public ResponseEntity<?> updateImagesOrder(@PathVariable Long id,
                                               @RequestBody List<ImageOrderDto> orderData) {
        try {
            log.info("Обновление порядка изображений для товара {}: {}", id, orderData);
            imageStorageService.updateImageOrder(orderData);
            return ResponseEntity.ok().body(Map.of("success", true, "message", "Порядок изображений обновлен"));
        } catch (Exception e) {
            log.error("Ошибка при обновлении порядка изображений", e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // МЕТОД ДЛЯ УДАЛЕНИЯ ИЗОБРАЖЕНИЙ
    private void removeImages(Product product, List<Long> imageIds) {
        log.info("Удаление изображений: {}", imageIds);

        // Создаем копию для безопасной итерации
        Set<ProductImage> imagesToRemove = new HashSet<>();

        for (ProductImage image : product.getImages()) {
            if (imageIds.contains(image.getId())) {
                imagesToRemove.add(image);
            }
        }

        // Удаляем из коллекции и с диска
        for (ProductImage image : imagesToRemove) {
            try {
                // Удаляем файл с диска
                imageStorageService.deleteImage(image.getUrl());
                // Удаляем из коллекции
                product.getImages().remove(image);
                log.info("Изображение {} удалено", image.getId());
            } catch (Exception e) {
                log.error("Ошибка при удалении изображения {}: {}", image.getId(), e.getMessage());
            }
        }
    }

    // МЕТОД ДЛЯ ДОБАВЛЕНИЯ НОВЫХ ИЗОБРАЖЕНИЙ
    private void addNewImages(Product product, List<MultipartFile> files,
                              List<String> alts, List<Integer> orders) {

        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);

            if (file != null && !file.isEmpty() && imageStorageService.isImage(file)) {
                try {
                    log.info("Сохранение изображения: {}", file.getOriginalFilename());

                    // Сохраняем изображение
                    ProductImage newImage = imageStorageService.saveImage(file, product.getTitle());
                    newImage.setProduct(product);

                    // Устанавливаем описание (alt)
                    if (alts != null && i < alts.size()) {
                        newImage.setAlt(alts.get(i));
                    }

                    // Устанавливаем порядок
                    int order = 0;
                    if (orders != null && i < orders.size()) {
                        order = orders.get(i);
                    }
                    newImage.setSortOrder(Math.max(1, order));

                    // Добавляем в коллекцию
                    if (product.getImages() == null) {
                        product.setImages(new HashSet<>());
                    }
                    product.getImages().add(newImage);

                    log.info("Изображение сохранено: {}", newImage.getUrl());

                } catch (Exception e) {
                    log.error("Ошибка при сохранении изображения {}: {}",
                            file.getOriginalFilename(), e.getMessage());
                }
            }
        }
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
                    } else {
                        // Автоматически устанавливаем порядок
                        productImage.setSortOrder(product.getImages().size() + 1);
                    }

                    productImage.setProduct(product);
                    product.getImages().add(productImage); // Добавляем в существующую коллекцию
                    log.info("ProductImage entity created and added to product");

                } catch (Exception e) {
                    log.error("Error saving image {}: {}", file.getOriginalFilename(), e.getMessage(), e);
                }
            } else {
                log.warn("Image {} is empty or not an image", file.getOriginalFilename());
            }
        }
    }

//    private void deleteProductImages(Product product, List<Long> imageIds) {
//        // Используем итератор для безопасного удаления
//        Iterator<ProductImage> iterator = product.getImages().iterator();
//        while (iterator.hasNext()) {
//            ProductImage image = iterator.next();
//            if (imageIds.contains(image.getId())) {
//                imageStorageService.deleteImage(image.getUrl());
//                iterator.remove(); // Удаляем через итератор
//            }
//        }
//    }

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
        log.info("Current values: {}", currentValues);

        model.addAttribute("product", product);
        model.addAttribute("attributes", allAttributes);
        model.addAttribute("currentValues", currentValues); // Это Map<Long, String>

        return "admin/products/specifications";
    }

    @PostMapping("/{id}/specifications")
    public String saveSpecifications(@PathVariable Long id,
                                     @RequestParam Map<String, String> allParams,
                                     RedirectAttributes redirectAttributes) {
        try {
            Product product = productService.findWithDetailsById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

            // Фильтруем только параметры атрибутов
            Map<Long, String> attributeValues = allParams.entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith("attr_"))
                    .filter(entry ->
                            entry.getValue() != null && !entry.getValue().trim().isEmpty()) // ← Игнорируем пустые
                    .collect(Collectors.toMap(
                            entry -> Long.parseLong(entry.getKey().substring(5)),
                            Map.Entry::getValue
                    ));

            attributeService.saveProductAttributes(product, attributeValues);

            redirectAttributes.addFlashAttribute("successMessage",
                    "Характеристики товара успешно сохранены");

        } catch (ValidationException e) {
            // Возвращаем на форму с ошибкой
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());

            // Также сохраняем введенные значения для повторного отображения
            redirectAttributes.addFlashAttribute("submittedValues", allParams);

            return "redirect:/admin/products/" + id + "/specifications";

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Ошибка при сохранении характеристик: " + e.getMessage());
        }

        return "redirect:/admin/products/" + id;
    }


}
