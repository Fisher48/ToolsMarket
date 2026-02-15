package ru.fisher.ToolsMarket.parsingXml;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.fisher.ToolsMarket.models.*;
import ru.fisher.ToolsMarket.repository.ProductAttributeValueRepository;

import javax.xml.stream.XMLStreamReader;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class YmlOfferImporter {

    private final ProductAttributeValueRepository productAttributeValueRepository;
    private static final int MAX_ATTRIBUTE_VALUE_LENGTH = 1024;

    public void importOffers(XMLStreamReader reader,
                             ImportContext ctx) throws Exception {

        while (reader.hasNext()) {
            reader.next();

            if (reader.isStartElement()
                    && reader.getLocalName().equals("offer")) {
                parseOffer(reader, ctx);
            }
        }
    }

    private void parseOffer(XMLStreamReader reader,
                            ImportContext ctx) throws Exception {

        String externalId = reader.getAttributeValue(null, "id");
        List<String> pictures = new ArrayList<>();

        String name = null;
        BigDecimal price = BigDecimal.ZERO;
        String categoryXmlId = null;
        String vendorCode = null;

        Map<String, String> params = new LinkedHashMap<>();

        while (reader.hasNext()) {
            reader.next();

            if (reader.isStartElement()) {
                switch (reader.getLocalName()) {
                    case "name" -> name = reader.getElementText();
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
                        String value = reader.getElementText();
                        params.put(paramName, value);
                    }
                }
            }

            if (reader.isEndElement()
                    && reader.getLocalName().equals("offer")) {
                break;
            }
        }

        String sku = (vendorCode != null && !vendorCode.isBlank())
                ? vendorCode
                : externalId;

        Category category = ctx.getCategoryByXmlId().get(categoryXmlId);

        if (category == null) {
            log.warn("Категория {} не найдена", categoryXmlId);
            return;
        }

        createOrUpdateProduct(sku, name, price, category, params, pictures, ctx);
    }

    private void createOrUpdateProduct(
            String sku,
            String name,
            BigDecimal price,
            Category category,
            Map<String, String> params,
            List<String> pictures,
            ImportContext ctx
    ) {

        Product product = ctx.getProductsBySku().get(sku);
        boolean isNew = false;

        if (product == null) {
            product = new Product();
            product.setSku(sku);
            product.setTitle(generateSlug(name) + "-" + sku);
            product.setCreatedAt(Instant.now());
            ctx.getProductsBySku().put(sku, product);
            isNew = true;
            // Очищаем старые изображения
            product.getImages().clear();
            log.debug("Обновляем товар: SKU={}, ID={}", sku, product.getId());
        }

        product.setName(name);
        product.setPrice(price);
        product.setProductType(ProductType.OTHER);
        product.setUpdatedAt(Instant.now());
        product.setActive(true);

        product.getCategories().clear();
        product.getCategories().add(category);

        if (isNew) {
            ctx.getProductsToSave().add(product);
        }

        log.debug("Создаем новый товар: SKU={}", sku);
        handleImages(product, pictures, ctx);
        handleAttributes(product, category, params, ctx);
    }

    private void handleImages(Product product, List<String> pictures, ImportContext ctx) {
        product.getImages().clear();
        int sort = 0;
        for (String url : pictures) {
            ProductImage image = ProductImage.builder()
                    .product(product)
                    .url(url)
                    .sortOrder(sort++)
                    .build();
            ctx.getProductImagesToSave().add(image);
            product.getImages().add(image);
        }
    }

    private void handleAttributes(Product product, Category category,
                                  Map<String, String> params, ImportContext ctx) {

        for (Map.Entry<String, String> entry : params.entrySet()) {

            String key = category.getId() + "_" + entry.getKey();

            Attribute attribute = ctx.getAttributeCache().get(key);
            if (attribute == null) {
                attribute = Attribute.builder()
                        .name(entry.getKey())
                        .category(category)
                        .type(AttributeType.STRING)
                        .build();

                ctx.getAttributeCache().put(key, attribute);
                ctx.getAttributesToSave().add(attribute);
            }

            // Используем комбинацию SKU + attributeName для кеша до сохранения продукта
            String valueKey = product.getSku() + "_" + attribute.getName();

            ProductAttributeValue pav = ctx.getValueCache().get(valueKey);

            if (pav == null) {
                // Если продукт уже существует в БД, проверяем, нет ли там значения
                if (product.getId() != null) {
                    pav = findAttributeValueInDb(product.getId(), attribute.getId());
                }
            }

            if (pav == null) {
                pav = ProductAttributeValue.builder()
                        .product(product)
                        .attribute(attribute)
                        .value(trimTo(entry.getValue(), MAX_ATTRIBUTE_VALUE_LENGTH)) // чтобы не превышать limit
                        .build();
                ctx.getValuesToSave().add(pav);
                ctx.getValueCache().put(valueKey, pav);
            } else {
                pav.setValue(trimTo(entry.getValue(), MAX_ATTRIBUTE_VALUE_LENGTH));
            }
        }
    }

    private ProductAttributeValue findAttributeValueInDb(Long productId, Long attributeId) {
        return productAttributeValueRepository
                .findByProductIdAndAttributeId(productId, attributeId)
                .orElse(null);
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

    /**
    * Генерация slug из названия
    */
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

