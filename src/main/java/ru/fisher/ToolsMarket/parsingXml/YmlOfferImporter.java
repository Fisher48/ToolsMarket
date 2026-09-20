package ru.fisher.ToolsMarket.parsingXml;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.fisher.ToolsMarket.models.*;
import ru.fisher.ToolsMarket.repository.AttributeRepository;
import ru.fisher.ToolsMarket.repository.ProductAttributeValueRepository;
import ru.fisher.ToolsMarket.repository.ProductRepository;

import javax.xml.stream.XMLStreamReader;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class YmlOfferImporter {

    private final ProductAttributeValueRepository productAttributeValueRepository;
    private final ProductRepository productRepository;
    private final AttributeRepository attributeRepository;
    private static final int MAX_ATTRIBUTE_VALUE_LENGTH = 1024;

    // Параметры из фида, которые по своей сути — маркетинговый/описательный текст,
    // а не характеристика товара. Переносятся в product.description вместо того,
    // чтобы храниться (и обрезаться) как обычный атрибут.
    // Сравнение регистронезависимое, с обрезкой пробелов.
    private static final Set<String> DESCRIPTION_PARAM_NAMES = Set.of(
            "комплектация и преимущества"
    );

    // ============================================================
    // Предварительный (лёгкий) проход — только собираем SKU офферов.
    // Нужен, чтобы одним запросом предзагрузить существующие товары
    // (см. StemYmlImportService) и не бить по БД по одному товару на оффер.
    // ============================================================
    /**
     * Считает, сколько офферов используют каждый vendorCode.
     * Нужен, чтобы найти коллизии (vendorCode, встречающийся у более чем одного
     * оффера) ДО резолва SKU — см. {@link #resolveSku}.
     */
    public Map<String, Integer> collectVendorCodeCounts(XMLStreamReader reader) throws Exception {
        Map<String, Integer> counts = new HashMap<>();

        while (reader.hasNext()) {
            reader.next();

            if (reader.isStartElement() && reader.getLocalName().equals("offer")) {
                while (reader.hasNext()) {
                    reader.next();

                    if (reader.isStartElement() && reader.getLocalName().equals("vendorCode")) {
                        String vendorCode = reader.getElementText();
                        if (vendorCode != null && !vendorCode.isBlank()) {
                            counts.merge(vendorCode, 1, Integer::sum);
                        }
                    }
                    if (reader.isEndElement() && reader.getLocalName().equals("offer")) {
                        break;
                    }
                }
            }
        }

        return counts;
    }

    public Set<String> collectSkus(XMLStreamReader reader,
                                   Set<String> collidingVendorCodes) throws Exception {
        Set<String> skus = new HashSet<>();

        while (reader.hasNext()) {
            reader.next();

            if (reader.isStartElement() && reader.getLocalName().equals("offer")) {
                String externalId = reader.getAttributeValue(null, "id");
                String vendorCode = null;

                while (reader.hasNext()) {
                    reader.next();

                    if (reader.isStartElement() && reader.getLocalName().equals("vendorCode")) {
                        vendorCode = reader.getElementText();
                    }
                    if (reader.isEndElement() && reader.getLocalName().equals("offer")) {
                        break;
                    }
                }

                String sku = resolveSku(externalId, vendorCode, collidingVendorCodes);
                if (sku != null && !sku.isBlank()) {
                    skus.add(sku);
                }
            }
        }

        return skus;
    }

    /**
     * Резолвит SKU оффера.
     *
     * Обычно SKU = vendorCode (либо offer id, если vendorCode отсутствует).
     * Но если vendorCode коллизирует (используется более чем одним оффером —
     * как КАТ020/ШЛА014 в фиде stem), к нему добавляется offer id
     * (формат: vendorCode + '-' + offerId), чтобы два РАЗНЫХ товара не слились
     * в один и импорт не падал на unique_product_attribute.
     */
    private String resolveSku(String externalId, String vendorCode,
                              Set<String> collidingVendorCodes) {
        boolean hasVendorCode = vendorCode != null && !vendorCode.isBlank();
        if (hasVendorCode && collidingVendorCodes.contains(vendorCode)) {
            return vendorCode + "-" + externalId;
        }
        return hasVendorCode ? vendorCode : externalId;
    }

    public void importOffers(XMLStreamReader reader,
                             ImportContext ctx,
                             boolean dryRun) throws Exception {

        while (reader.hasNext()) {
            reader.next();

            if (reader.isStartElement()
                    && reader.getLocalName().equals("offer")) {
                parseOffer(reader, ctx, dryRun);
            }
        }
    }

    public void parseOffer(XMLStreamReader reader,
                           ImportContext ctx,
                           boolean dryRun) throws Exception {

        String externalId = reader.getAttributeValue(null, "id");
        List<String> pictures = new ArrayList<>();

        String name = null;
        String description = null;
        BigDecimal price = BigDecimal.ZERO;
        String categoryXmlId = null;
        String vendorCode = null;

        Map<String, String> params = new LinkedHashMap<>();
        // unit каждого param (по тому же ключу, что и params) — нужен,
        // чтобы сохранить единицу измерения в Attribute.unit при создании атрибута
        Map<String, String> paramUnits = new HashMap<>();

        while (reader.hasNext()) {
            reader.next();

            if (reader.isStartElement()) {
                switch (reader.getLocalName()) {
                    case "name" -> name = reader.getElementText();
                    case "description" -> description = reader.getElementText();
                    case "price" -> {
                        try {
                            price = new BigDecimal(reader.getElementText());
                        } catch (Exception ignored) {}
                    }
                    case "vendorCode" ->
                            vendorCode = reader.getElementText();
                    case "categoryId" ->
                            categoryXmlId = reader.getElementText();
                    case "picture" -> {
                        String pic = reader.getElementText();
                        if (pic != null && !pic.isBlank()) {
                            pictures.add(pic.trim());
                        }
                    }
                    case "param" -> {
                        String paramName =
                                reader.getAttributeValue(null, "name");
                        String unit = reader.getAttributeValue(null, "unit");
                        String value = reader.getElementText();

                        // Пропускаем параметры без имени — иначе получим
                        // "мусорный" атрибут с ключом "<categoryId>_null"
                        if (paramName == null || paramName.isBlank()) {
                            log.warn("Оффер {}: параметр без атрибута name пропущен (value={})",
                                    externalId, value);
                        } else if (!params.containsKey(paramName)) {
                            params.put(paramName, value);
                            if (unit != null && !unit.isBlank()) {
                                paramUnits.put(paramName, unit);
                            }
                        } else {
                            // В реальном фиде встречается повтор одного и того же name
                            // с разным unit (например, "Мощность" в лс и отдельно в Вт).
                            // Различаем по unit, чтобы сохранить оба.
                            String disambiguatedKey = (unit != null && !unit.isBlank())
                                    ? paramName + " (" + unit + ")"
                                    : paramName + " (доп.)";
                            params.put(disambiguatedKey, value);
                            if (unit != null && !unit.isBlank()) {
                                paramUnits.put(disambiguatedKey, unit);
                            }
                            log.debug("Оффер {}: повторный параметр '{}' сохранён как '{}'",
                                    externalId, paramName, disambiguatedKey);
                        }
                    }
                }
            }

            if (reader.isEndElement()
                    && reader.getLocalName().equals("offer")) {
                break;
            }
        }

        String sku = resolveSku(externalId, vendorCode, ctx.getCollidingVendorCodes());

        Category category = ctx.getCategoryByXmlId().get(categoryXmlId);

        if (category == null) {
            log.warn("Категория {} не найдена, оффер {} (sku={}) пропущен", categoryXmlId, externalId, sku);
            return;
        }

        // Переносим "текстовые" параметры (маркетинговый текст, часто в тысячи
        // символов, иногда с HTML-таблицами) в описание вместо атрибута —
        // как обычный атрибут они не фильтруются и режутся до 1024 символов.
        StringBuilder extraDescription = new StringBuilder();
        Iterator<Map.Entry<String, String>> paramIterator = params.entrySet().iterator();
        while (paramIterator.hasNext()) {
            Map.Entry<String, String> entry = paramIterator.next();
            String normalizedName = entry.getKey() == null ? "" : entry.getKey().trim().toLowerCase();
            boolean isDescriptionLikeParam = DESCRIPTION_PARAM_NAMES.contains(normalizedName)
                    || (entry.getValue() != null && entry.getValue().length() > MAX_ATTRIBUTE_VALUE_LENGTH);

            if (isDescriptionLikeParam) {
                if (extraDescription.length() > 0) {
                    extraDescription.append("\n\n");
                }
                extraDescription.append(entry.getKey()).append(":\n").append(entry.getValue());
                paramIterator.remove();
                paramUnits.remove(entry.getKey());
            }
        }

        String fullDescription = combineDescription(description, extraDescription.toString());

        createOrUpdateProduct(sku, name, fullDescription, price, category, params, paramUnits, pictures, ctx, dryRun);
    }

    private String combineDescription(String baseDescription, String extra) {
        String base = baseDescription == null ? "" : baseDescription.trim();
        String extraTrimmed = extra == null ? "" : extra.trim();

        if (extraTrimmed.isEmpty()) {
            return base.isEmpty() ? null : base;
        }
        if (base.isEmpty()) {
            return extraTrimmed;
        }
        return base + "\n\n" + extraTrimmed;
    }

    private void createOrUpdateProduct(String sku, String name, String description, BigDecimal price,
                                       Category category, Map<String, String> params,
                                       Map<String, String> paramUnits,
                                       List<String> pictures, ImportContext ctx,
                                       boolean dryRun) {

        Product product = ctx.getProductsBySku().get(sku);

        if (product == null) {
            // Подстраховка: если по какой-то причине SKU не попал в предзагруженный
            // кэш (см. StemYmlImportService.collectSkus), fallback на точечный запрос.
            product = productRepository.findBySku(sku).orElse(null);
        }

        boolean isNew = false;

        if (product == null) {
            product = new Product();
            product.setSku(sku);
            product.setTitle(generateSlug(name) + "-" + sku);
            product.setCreatedAt(Instant.now());
            // Только для НОВЫХ товаров выставляем значения по умолчанию.
            // Существующие товары не трогаем — иначе импорт каждый раз сбрасывал бы
            // active / productType / категории
            product.setActive(true);
            product.setProductType(ProductType.OTHER);
            product.setCurrency("RUB");
            isNew = true;
            log.info("НОВЫЙ: SKU={}, name={}", sku, name);
        }

        // Захватываем значения ДО мутации — нужны для отчёта "было → стало"
        String oldName = isNew ? null : product.getName();
        BigDecimal oldPrice = isNew ? null : product.getPrice();
        int oldImageCount = (!isNew && product.getImages() != null) ? product.getImages().size() : 0;

        // Проверяем, изменилось ли что-то
        boolean changed = false;
        boolean nameChanged = false;
        boolean priceChanged = false;
        boolean descriptionChanged = false;

        if (!Objects.equals(product.getName(), name)) {
            log.info("  ИМЯ: {} → {}", product.getName(), name);
            product.setName(name);
            changed = true;
            nameChanged = true;
        }
        if (product.getPrice().compareTo(price) != 0) {
            log.info("  ЦЕНА: {} → {}", product.getPrice(), price);
            product.setPrice(price);
            changed = true;
            priceChanged = true;
        }
        if (!Objects.equals(product.getDescription(), description)) {
            product.setDescription(description);
            changed = true;
            descriptionChanged = true;
        }

        // Проверяем картинки
        int newImageCount = pictures.size();

        Set<String> oldUrls = new HashSet<>();
        if (product.getImages() != null) {
            for (ProductImage img : product.getImages()) {
                oldUrls.add(img.getUrl());
            }
        }
        Set<String> newUrls = new HashSet<>(pictures);

        boolean imagesChanged = oldImageCount != newImageCount || !oldUrls.equals(newUrls);
        if (imagesChanged) {
            log.info("  КАРТИНКИ: SKU={} | было={} → стало={}", sku, oldImageCount, newImageCount);
            changed = true;
        }

        handleImages(product, pictures, ctx);
        boolean attributesChanged = handleAttributes(product, category, params, paramUnits, ctx, dryRun);
        if (attributesChanged) {
            changed = true;
        }

        product.setUpdatedAt(Instant.now());

        // Категорию из фида привязываем: для новых — всегда, для существующих —
        // только если товар ещё нет в этой категории (чтобы не затирать вручную
        // назначенные категории при каждом импорте).
        if (isNew || product.getCategories().isEmpty()) {
            product.getCategories().clear();
            product.getCategories().add(category);
        }

        ctx.getProductsBySku().put(sku, product);

        if (isNew) {
            ctx.getNewSkus().add(sku);
        }

        // Добавляем в список на сохранение
        if (changed || isNew) {
            ctx.getProductsToSave().add(product);

            ctx.getChanges().add(ProductChangeSummary.builder()
                    .sku(sku)
                    .changeType(isNew ? ProductChangeSummary.ChangeType.NEW : ProductChangeSummary.ChangeType.UPDATED)
                    .oldName(oldName)
                    .newName(product.getName())
                    .nameChanged(nameChanged)
                    .oldPrice(oldPrice)
                    .newPrice(product.getPrice())
                    .priceChanged(priceChanged)
                    .oldImageCount(oldImageCount)
                    .newImageCount(newImageCount)
                    .imagesChanged(imagesChanged)
                    .descriptionChanged(descriptionChanged)
                    .attributesChanged(attributesChanged)
                    .build());
        }
    }

    private void handleImages(Product product, List<String> pictures, ImportContext ctx) {
        Map<String, ProductImage> existingByUrl = new HashMap<>();
        for (ProductImage img : product.getImages()) {
            existingByUrl.put(img.getUrl(), img);
        }

        product.getImages().clear();

        int sort = 0;
        for (String url : pictures) {
            ProductImage image = existingByUrl.get(url);
            if (image != null) {
                image.setSortOrder(sort++);
                product.getImages().add(image);
            } else {
                image = ProductImage.builder()
                        .product(product)
                        .url(url)
                        .sortOrder(sort++)
                        .build();
                product.getImages().add(image);
            }
        }
        // Старые изображения, которых нет в новом списке, удалятся каскадом
        // благодаря orphanRemoval = true на Product.images
    }

    /**
     * Синхронизирует значения атрибутов товара с параметрами из фида.
     * Возвращает true, если что-то реально изменилось (для флага changed).
     *
     * Важно: изменения ОБЯЗАТЕЛЬНО зеркалятся в product.getAttributeValues(),
     * иначе orphanRemoval не увидит удалённые значения, а Hibernate не будет
     * гарантированно отслеживать точечные правки значений без явного save().
     */
    private boolean handleAttributes(Product product, Category category,
                                     Map<String, String> params, Map<String, String> paramUnits,
                                     ImportContext ctx, boolean dryRun) {

        boolean changed = false;
        Set<Long> keepAttributeIds = new HashSet<>();

        for (Map.Entry<String, String> entry : params.entrySet()) {
            String paramName = entry.getKey().trim();

            String key = category.getId() + "_" + paramName;
            Attribute attribute = ctx.getAttributeCache().get(key);

            if (attribute == null) {
                if (dryRun) {
                    // В предпросмотре не ходим в БД за точечным атрибутом и не сохраняем:
                    // кэш уже наполнен attributeRepository.findAll() в сервисе. Если атрибута
                    // нет в кэше, считаем его новым и создаём в памяти (без save).
                    attribute = Attribute.builder()
                            .name(paramName)
                            .unit(paramUnits.get(paramName))
                            .category(category)
                            .type(AttributeType.STRING)
                            .build();
                } else {
attribute = attributeRepository.findFirstByCategoryIdAndNameOrderByIdAsc(category.getId(), paramName)
                        .orElse(null);

                    if (attribute == null) {
                        attribute = attributeRepository.save(
                                Attribute.builder()
                                        .name(paramName)
                                        .unit(paramUnits.get(paramName))
                                        .category(category)
                                        .type(AttributeType.STRING)
                                        .build()
                        );
                    }
                }

                ctx.getAttributeCache().put(key, attribute);
            }

            keepAttributeIds.add(attribute.getId());

            String valueKey = product.getSku() + "_" + attribute.getName();
            ProductAttributeValue pav = ctx.getValueCache().get(valueKey);

            if (pav == null && product.getId() != null && !dryRun) {
                pav = findAttributeValueInDb(product.getId(), attribute.getId());
            }

            // Подстраховка от дублей (product, attribute): если для этой пары уже есть
            // значение, созданное в текущем проходе (в т.ч. транзиентное), — переиспользуем
            // его, а не создаём второй PAV. Иначе возможен INSERT с нарушением
            // unique_product_attribute (например, при коллизии vendorCode в фиде).
            if (pav == null) {
                pav = findInProductAttributeValues(product, attribute);
            }

            String newValue = trimTo(entry.getValue(), MAX_ATTRIBUTE_VALUE_LENGTH);

            if (pav == null) {
                pav = ProductAttributeValue.builder()
                        .product(product)
                        .attribute(attribute)
                        .value(newValue)
                        .build();
                // Держим бидирекциональную связь синхронной — нужно для
                // orphanRemoval и для корректной работы Product.getAttributeValue(...)
                product.getAttributeValues().add(pav);
                ctx.getValuesToSave().add(pav);
                ctx.getValueCache().put(valueKey, pav);
                changed = true;
            } else if (!Objects.equals(pav.getValue(), newValue)) {
                pav.setValue(newValue);
                // Явно добавляем в список на сохранение — не полагаемся
                // на неявный dirty checking, который зависит от границ транзакции.
                ctx.getValuesToSave().add(pav);
                ctx.getValueCache().put(valueKey, pav);
                changed = true;
            }
        }

        // Удаляем значения атрибутов ЭТОЙ категории, которых больше нет в фиде.
        // В dry-run категория нового товара имеет синтетический id — не выполняем
        // удаление, чтобы не задеть связанные с реальным атрибутом значения.
        boolean removed = false;
        if (!(dryRun && category.getId() != null && category.getId() < 0)) {
            removed = product.getAttributeValues().removeIf(av ->
                    av.getAttribute() != null
                            && av.getAttribute().getCategory() != null
                            && av.getAttribute().getCategory().getId().equals(category.getId())
                            && !keepAttributeIds.contains(av.getAttribute().getId()));
        }

        return changed || removed;
    }

    private ProductAttributeValue findAttributeValueInDb(Long productId, Long attributeId) {
        return productAttributeValueRepository
                .findByProductIdAndAttributeId(productId, attributeId)
                .orElse(null);
    }

    /**
     * Ищет в коллекции товара значение атрибута по id атрибута.
     * Покрывает и уже сохранённые, и транзиентные (ещё не в БД) значения,
     * созданные в текущем проходе — защита от создания второго PAV для пары
     * (product, attribute).
     */
    private ProductAttributeValue findInProductAttributeValues(Product product, Attribute attribute) {
        if (product.getAttributeValues() == null || attribute == null) return null;
        Long attributeId = attribute.getId();
        if (attributeId == null) return null;
        for (ProductAttributeValue pav : product.getAttributeValues()) {
            if (pav.getAttribute() != null && attributeId.equals(pav.getAttribute().getId())) {
                return pav;
            }
        }
        return null;
    }

    private String trimTo(String value, int maxLength) {
        if (value == null) return null;

        if (value.length() > maxLength) {
            log.warn("Обрезано значение атрибута. Было: {} символов",
                    value.length());
            return value.substring(0, maxLength);
        }
        return value;
    }

    private String generateSlug(String name) {
        if (name == null || name.isEmpty()) {
            return "product-" + System.currentTimeMillis();
        }

        String slug = transliterate(name)
                .replaceAll("[^a-zA-Z0-9\\s-]", "")
                .replaceAll("\\s+", "_")
                .replaceAll("-+", "_")
                .toLowerCase();

        slug = slug.replaceAll("^_+|_+$", "");
        slug = slug.replaceAll("_+", "_");

        if (slug.length() > 200) {
            slug = slug.substring(0, 200);
        }

        if (slug.endsWith("_")) {
            slug = slug.substring(0, slug.length() - 1);
        }

        return slug;
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

        // Заглавные
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
}