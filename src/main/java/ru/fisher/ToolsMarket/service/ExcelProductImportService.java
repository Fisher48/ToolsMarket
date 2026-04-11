package ru.fisher.ToolsMarket.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ru.fisher.ToolsMarket.dto.ExcelImportResult;
import ru.fisher.ToolsMarket.models.Category;
import ru.fisher.ToolsMarket.models.Product;
import ru.fisher.ToolsMarket.models.ProductImage;
import ru.fisher.ToolsMarket.models.ProductType;
import ru.fisher.ToolsMarket.repository.CategoryRepository;
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
    private final CategoryRepository categoryRepository;

    // Категория по умолчанию для импортированных товаров
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

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            // Получаем первый лист
            Sheet sheet = workbook.getSheetAt(0);
            result.setTotalRows(sheet.getPhysicalNumberOfRows() - 1); // минус заголовок

            // Определяем индексы столбцов
            Map<String, Integer> columnIndexes = findColumnIndexes(sheet.getRow(0));

            // Проверяем наличие обязательных столбцов
            if (!columnIndexes.containsKey("Артикул") || !columnIndexes.containsKey("Наименование элемента")) {
                result.addError("В файле отсутствуют обязательные столбцы: 'Артикул' и/или 'Наименование элемента'");
                return result;
            }

            // Получаем категорию по умолчанию
            Category defaultCategory = getOrCreateDefaultCategory();

            // Обрабатываем строки
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                try {
                    processRow(row, columnIndexes, defaultCategory, result);
                } catch (Exception e) {
                    log.error("Ошибка при обработке строки {}: {}", i + 1, e.getMessage());
                    result.addError("Строка " + (i + 1) + ": " + e.getMessage());
                }
            }

            log.info("Импорт завершен. Создано: {}, Пропущено: {}, Ошибок: {}",
                    result.getCreated(), result.getSkipped(), result.getErrors());

        } catch (Exception e) {
            log.error("Ошибка при чтении Excel файла", e);
            result.addError("Ошибка чтения файла: " + e.getMessage());
        }

        return result;
    }

    /**
     * Определение индексов столбцов
     */
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

    /**
     * Обработка одной строки
     */
    private void processRow(Row row, Map<String, Integer> columnIndexes,
                            Category defaultCategory, ExcelImportResult result) {

        // Получаем артикул (обязательное поле)
        Integer skuIndex = columnIndexes.get("Артикул");
        if (skuIndex == null) {
            result.addError("Не найден столбец 'Артикул'");
            return;
        }

        String sku = getCellValueAsString(row.getCell(skuIndex));
        if (sku == null || sku.trim().isEmpty()) {
            result.incrementErrors();
            result.addError("Пустой артикул");
            return;
        }
        sku = sku.trim();

        // Проверяем, существует ли товар с таким артикулом
        if (productRepository.findBySku(sku).isPresent()) {
            result.incrementSkipped();
            result.addSkippedProduct(sku, "уже существует");
            log.debug("Товар с артикулом {} уже существует, пропускаем", sku);
            return;
        }

        // Получаем наименование (обязательное поле)
        Integer nameIndex = columnIndexes.get("Наименование элемента");
        if (nameIndex == null) {
            result.incrementErrors();
            result.addError("Не найден столбец 'Наименование элемента'");
            return;
        }

        String name = getCellValueAsString(row.getCell(nameIndex));
        if (name == null || name.trim().isEmpty()) {
            result.incrementErrors();
            result.addError("Пустое наименование для артикула " + sku);
            return;
        }
        name = name.trim();

        // Получаем описание (необязательное)
        Integer descIndex = columnIndexes.get("Детальное описание");
        String description = descIndex != null ?
                getCellValueAsString(row.getCell(descIndex)) : null;

        // Получаем картинку (необязательная)
        Integer imageIndex = columnIndexes.get("Детальная картинка");
        String imageUrl = imageIndex != null ?
                getCellValueAsString(row.getCell(imageIndex)) : null;

        // Создаем товар
        Product product = createProduct(sku, name, description, imageUrl, defaultCategory);

        result.incrementCreated();
        result.addCreatedProduct(sku);
        log.info("Создан товар: SKU={}, наименование={}", sku, name);
    }

    /**
     * Создание товара
     */
    private Product createProduct(String sku, String name, String description,
                                  String imageUrl, Category category) {

        // Генерируем title из имени + sku для уникальности
        String title = generateTitle(name) + "-" + sku.toLowerCase();

        Product product = Product.builder()
                .sku(sku)
                .name(name)
                .title(title)
                .description(description)
                .shortDescription(generateShortDescription(description, name))
                .price(DEFAULT_PRICE)
                .categories(new HashSet<>())
                .images(new HashSet<>())
                .currency("RUB")
                .active(true)
                .productType(ProductType.OTHER)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        // Добавляем категорию
        product.getCategories().add(category);

        // Добавляем изображение, если есть
        if (imageUrl != null && !imageUrl.trim().isEmpty()) {
            ProductImage image = ProductImage.builder()
                    .product(product)
                    .url(imageUrl.trim())
                    .sortOrder(0)
                    .build();
            product.getImages().add(image);
        }

        return productRepository.save(product);
    }

    /**
     * Получение или создание категории по умолчанию
     */
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