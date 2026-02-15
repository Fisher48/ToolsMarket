package ru.fisher.ToolsMarket.parsingXml;

import lombok.Getter;
import ru.fisher.ToolsMarket.models.*;
import ru.fisher.ToolsMarket.repository.AttributeRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Getter
public class ImportContext {

    private final Map<String, Category> categoryByXmlId;
    private final Map<String, Product> productsBySku;
    private final Map<String, Attribute> attributeCache;
    private final Map<String, ProductAttributeValue> valueCache;

    private final List<Product> productsToSave = new ArrayList<>();
    private final List<Attribute> attributesToSave = new ArrayList<>();
    private final List<ProductAttributeValue> valuesToSave = new ArrayList<>();
    private final List<ProductImage> productImagesToSave = new ArrayList<>();

    // Добавляем репозиторий для проверки существующих атрибутов
    private AttributeRepository attributeRepository;

    public ImportContext(
            Map<String, Category> categoryByXmlId,
            Map<String, Product> productsBySku,
            Map<String, Attribute> attributeCache,
            Map<String, ProductAttributeValue> valueCache
    ) {
        this.categoryByXmlId = categoryByXmlId;
        this.productsBySku = productsBySku;
        this.attributeCache = attributeCache;
        this.valueCache = valueCache;
    }
}

