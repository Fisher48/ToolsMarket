package ru.fisher.ToolsMarket.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ru.fisher.ToolsMarket.dto.ExcelImportResult;
import ru.fisher.ToolsMarket.models.Category;
import ru.fisher.ToolsMarket.models.Product;
import ru.fisher.ToolsMarket.models.ProductImage;
import ru.fisher.ToolsMarket.models.ProductType;
import ru.fisher.ToolsMarket.repository.CategoryRepository;
import ru.fisher.ToolsMarket.repository.ProductImageRepository;
import ru.fisher.ToolsMarket.repository.ProductRepository;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExcelProductImportService {

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final CategoryRepository categoryRepository;

    // Размер пакета для массовой вставки
    @Value("${app.import.batch-size}")
    private int batchSize;

    private static final String DEFAULT_CATEGORY_TITLE = "ruchnoy_instrument";
    private static final BigDecimal DEFAULT_PRICE = BigDecimal.ZERO;

    /**
     * Импорт товаров из Excel файла
     */
    @Transactional
    public ExcelImportResult importFromExcel(MultipartFile file) {
        ExcelImportResult result = new ExcelImportResult();

        if (file.isEmpty()) {
            result.addError("Файл пустой");
            return result;
        }

        // Проверяем расширение файла
        String fileName = file.getOriginalFilename();
        if (fileName == null || (!fileName.endsWith(".xlsx") && !fileName.endsWith(".xls"))) {
            result.addError("Поддерживаются только файлы Excel (.xlsx, .xls)");
            return result;
        }

        long startTime = System.currentTimeMillis();

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            // Получаем первый лист
            Sheet sheet = workbook.getSheetAt(0);
            int totalRows = sheet.getPhysicalNumberOfRows() - 1;
            result.setTotalRows(totalRows);

            log.info("Начинаем импорт {} строк из Excel", totalRows);

            Map<String, Integer> columnIndexes = findColumnIndexes(sheet.getRow(0));

            // Проверяем наличие обязательных столбцов
            if (!columnIndexes.containsKey("Артикул") || !columnIndexes.containsKey("Наименование элемента")) {
                result.addError("В файле отсутствуют обязательные столбцы: 'Артикул' и/или 'Наименование элемента'");
                return result;
            }

            // Получаем категорию по умолчанию
            Category defaultCategory = getOrCreateDefaultCategory();

            // Собираем все существующие SKU для быстрой проверки
            Set<String> existingSkus = new HashSet<>(productRepository.findAllSkus());
            log.info("Загружено {} существующих SKU", existingSkus.size());

            // Списки для пакетного сохранения
            List<Product> productsToSave = new ArrayList<>();
            List<ProductImage> imagesToSave = new ArrayList<>();

            int processed = 0;
            int batchCounter = 0;

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                try {
                    Product product = processRowToProduct(row, columnIndexes, defaultCategory, existingSkus, result);

                    if (product != null) {
                        productsToSave.add(product);

                        // Если есть изображение, добавляем отдельно
                        String imageUrl = getImageUrlFromRow(row, columnIndexes);
                        if (imageUrl != null && !imageUrl.trim().isEmpty()) {
                            ProductImage image = new ProductImage();
                            image.setProduct(product);
                            image.setUrl(imageUrl.trim());
                            image.setSortOrder(0);
                            imagesToSave.add(image);
                        }

                        batchCounter++;
                        processed++;

                        // Сохраняем пакет при достижении лимита
                        if (batchCounter >= batchSize) {
                            saveBatch(productsToSave, imagesToSave, result);
                            productsToSave.clear();
                            imagesToSave.clear();
                            batchCounter = 0;

                            log.info("Сохранен пакет из {} товаров. Прогресс: {}/{}",
                                    batchSize, processed, result.getTotalRows());
                        }
                    }

                } catch (Exception e) {
                    log.error("Ошибка при обработке строки {}: {}", i + 1, e.getMessage());
                    result.addError("Строка " + (i + 1) + ": " + e.getMessage());
                }
            }

            // Сохраняем остатки
            if (!productsToSave.isEmpty()) {
                saveBatch(productsToSave, imagesToSave, result);
            }

            long endTime = System.currentTimeMillis();
            log.info("Импорт завершен за {} мс. Создано: {}, Пропущено: {}, Ошибок: {}",
                    (endTime - startTime), result.getCreated(), result.getSkipped(), result.getErrors());

        } catch (Exception e) {
            log.error("Ошибка при чтении Excel файла", e);
            result.addError("Ошибка чтения файла: " + e.getMessage());
        }

        return result;
    }

    /**
     * Пакетное сохранение товаров и изображений
     */
    private void saveBatch(List<Product> products, List<ProductImage> images, ExcelImportResult result) {
        if (products.isEmpty()) return;

        // Сохраняем товары пакетно
        List<Product> savedProducts = productRepository.saveAll(products);
        result.incrementCreated(savedProducts.size());

        for (Product product : savedProducts) {
            result.addCreatedProduct(product.getSku());
        }

        // Сохраняем изображения
        productImageRepository.saveAll(images);

        log.debug("Сохранен пакет: {} товаров, {} изображений", savedProducts.size(), images.size());
    }

    /**
     * Преобразование строки в объект Product (без сохранения)
     */
    private Product processRowToProduct(Row row, Map<String, Integer> columnIndexes,
                                        Category defaultCategory, Set<String> existingSkus,
                                        ExcelImportResult result) {

        Integer skuIndex = columnIndexes.get("Артикул");
        if (skuIndex == null) {
            result.addError("Не найден столбец 'Артикул'");
            return null;
        }

        String sku = getCellValueAsString(row.getCell(skuIndex));
        if (sku == null || sku.trim().isEmpty()) {
            result.incrementErrors();
            result.addError("Пустой артикул");
            return null;
        }
        sku = sku.trim();

        // Быстрая проверка существования через Set
        if (existingSkus.contains(sku)) {
            result.incrementSkipped();
            result.addSkippedProduct(sku, "уже существует");
            return null;
        }

        // Добавляем в Set, чтобы избежать дубликатов в рамках одного импорта
        existingSkus.add(sku);

        Integer nameIndex = columnIndexes.get("Наименование элемента");
        if (nameIndex == null) {
            result.incrementErrors();
            result.addError("Не найден столбец 'Наименование элемента'");
            return null;
        }

        String name = getCellValueAsString(row.getCell(nameIndex));
        if (name == null || name.trim().isEmpty()) {
            result.incrementErrors();
            result.addError("Пустое наименование для артикула " + sku);
            return null;
        }
        name = name.trim();

        // Получаем описание (необязательное)
        Integer descIndex = columnIndexes.get("Детальное описание");
        String description = descIndex != null ?
                getCellValueAsString(row.getCell(descIndex)) : null;

        // Создаем товар
        Product product = createProduct(sku, name, description, defaultCategory);

        result.incrementCreated();
        result.addCreatedProduct(sku);
        log.info("Создан товар: SKU={}, наименование={}", sku, name);
        return product;
    }

    /**
     * Создание товара
     */
    private Product createProduct(String sku, String name, String description, Category category) {

        // Генерируем title из имени + sku для уникальности
        String title = generateTitle(name) + "-" + sku.toLowerCase();

        // Создаем товар
        Product product = new Product();
        product.setSku(sku);
        product.setName(name);
        product.setTitle(title);
        product.setDescription(description);
        product.setShortDescription(generateShortDescription(description, name));
        product.setPrice(DEFAULT_PRICE);
        product.setCurrency("RUB");
        product.setActive(true);
        product.setProductType(ProductType.OTHER);
        product.setCreatedAt(Instant.now());
        product.setUpdatedAt(Instant.now());

        // Инициализируем коллекции
        if (product.getCategories() == null) {
            product.setCategories(new HashSet<>());
        }
        if (product.getImages() == null) {
            product.setImages(new LinkedHashSet<>());
        }
        if (product.getAttributeValues() == null) {
            product.setAttributeValues(new LinkedHashSet<>());
        }

        product.getCategories().add(category);

        return product;
    }

    /**
     * Получение URL изображения из строки
     */
    private String getImageUrlFromRow(Row row, Map<String, Integer> columnIndexes) {
        Integer imageIndex = columnIndexes.get("Детальная картинка");
        if (imageIndex != null) {
            return getCellValueAsString(row.getCell(imageIndex));
        }
        return null;
    }

    private Map<String, Integer> findColumnIndexes(Row headerRow) {
        Map<String, Integer> indexes = new HashMap<>();

        if (headerRow == null) return indexes;

        for (Cell cell : headerRow) {
            String cellValue = getCellValueAsString(cell).trim();

            if (cellValue.contains("Артикул") || cellValue.contains("артикул") ||
                    cellValue.contains("SKU") || cellValue.contains("sku")) {
                indexes.put("Артикул", cell.getColumnIndex());
            }
            else if (cellValue.contains("Наименование") || cellValue.contains("наименование") ||
                    cellValue.contains("Название") || cellValue.contains("название")) {
                indexes.put("Наименование элемента", cell.getColumnIndex());
            }
            else if (cellValue.contains("Картинка") || cellValue.contains("картинка") ||
                    cellValue.contains("Изображение") || cellValue.contains("изображение") ||
                    cellValue.contains("Фото") || cellValue.contains("фото")) {
                indexes.put("Детальная картинка", cell.getColumnIndex());
            }
            else if (cellValue.contains("Описание") || cellValue.contains("описание") ||
                    cellValue.contains("Description") || cellValue.contains("description")) {
                indexes.put("Детальное описание", cell.getColumnIndex());
            }
        }

        return indexes;
    }

    private Category getOrCreateDefaultCategory() {
        return categoryRepository.findByTitle(DEFAULT_CATEGORY_TITLE)
                .orElseGet(() -> {
                    Category newCategory = Category.builder()
                            .name("Импортированные товары")
                            .title(DEFAULT_CATEGORY_TITLE)
                            .description("Товары, импортированные из Excel")
                            .sortOrder(999)
                            .createdAt(Instant.now())
                            .build();
                    return categoryRepository.save(newCategory);
                });
    }

    /**
     * Генерация shortDescription
     */
    private String generateShortDescription(String description, String name) {
        if (description != null && !description.isEmpty()) {
            if (description.length() <= 200) {
                return description;
            }
            return description.substring(0, 197) + "...";
        }
        return name.length() <= 200 ? name : name.substring(0, 197) + "...";
    }

    /**
     * Генерация title из названия
     */
    private String generateTitle(String name) {
        if (name == null || name.isEmpty()) {
            return "product-" + System.currentTimeMillis();
        }

        String title = transliterate(name)
                .replaceAll("[^a-zA-Z0-9\\s-]", "")
                .replaceAll("\\s+", "_")
                .replaceAll("-+", "_")
                .toLowerCase();

        title = title.replaceAll("^_+|_+$", "");
        title = title.replaceAll("_+", "_");

        if (title.length() > 150) {
            title = title.substring(0, 150);
        }

        if (title.endsWith("_")) {
            title = title.substring(0, title.length() - 1);
        }

        return title;
    }

    /**
     * Транслитерация
     */
    private String transliterate(String text) {
        Map<Character, String> map = new HashMap<>();
        map.put('а', "a"); map.put('б', "b"); map.put('в', "v"); map.put('г', "g");
        map.put('д', "d"); map.put('е', "e"); map.put('ё', "yo"); map.put('ж', "zh");
        map.put('з', "z"); map.put('и', "i"); map.put('й', "y"); map.put('к', "k");
        map.put('л', "l"); map.put('м', "m"); map.put('н', "n"); map.put('о', "o");
        map.put('п', "p"); map.put('р', "r"); map.put('с', "s"); map.put('т', "t");
        map.put('у', "u"); map.put('ф', "f"); map.put('х', "kh"); map.put('ц', "ts");
        map.put('ч', "ch"); map.put('ш', "sh"); map.put('щ', "shch"); map.put('ъ', "");
        map.put('ы', "y"); map.put('ь', ""); map.put('э', "e"); map.put('ю', "yu");
        map.put('я', "ya");

        map.put('А', "A"); map.put('Б', "B"); map.put('В', "V"); map.put('Г', "G");
        map.put('Д', "D"); map.put('Е', "E"); map.put('Ё', "Yo"); map.put('Ж', "Zh");
        map.put('З', "Z"); map.put('И', "I"); map.put('Й', "Y"); map.put('К', "K");
        map.put('Л', "L"); map.put('М', "M"); map.put('Н', "N"); map.put('О', "O");
        map.put('П', "P"); map.put('Р', "R"); map.put('С', "S"); map.put('Т', "T");
        map.put('У', "U"); map.put('Ф', "F"); map.put('Х', "Kh"); map.put('Ц', "Ts");
        map.put('Ч', "Ch"); map.put('Ш', "Sh"); map.put('Щ', "Shch"); map.put('Ъ', "");
        map.put('Ы', "Y"); map.put('Ь', ""); map.put('Э', "E"); map.put('Ю', "Yu");
        map.put('Я', "Ya");

        StringBuilder result = new StringBuilder();
        for (char c : text.toCharArray()) {
            result.append(map.getOrDefault(c, String.valueOf(c)));
        }
        return result.toString();
    }

    /**
     * Получение значения ячейки как строки
     */
    private String getCellValueAsString(Cell cell) {
        if (cell == null) return null;

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    // Для числовых значений
                    double numericValue = cell.getNumericCellValue();
                    if (numericValue == (long) numericValue) {
                        return String.valueOf((long) numericValue);
                    }
                    return String.valueOf(numericValue);
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue();
                } catch (IllegalStateException e) {
                    return String.valueOf(cell.getNumericCellValue());
                }
            default:
                return null;
        }
    }
}