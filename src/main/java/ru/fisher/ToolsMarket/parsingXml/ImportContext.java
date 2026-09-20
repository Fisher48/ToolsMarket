package ru.fisher.ToolsMarket.parsingXml;

import lombok.Getter;
import lombok.Setter;
import ru.fisher.ToolsMarket.models.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Getter
public class ImportContext {

    private final Map<String, Category> categoryByXmlId;
    private final Map<String, Product> productsBySku;
    private final Map<String, Attribute> attributeCache;
    private final Map<String, ProductAttributeValue> valueCache;

    // Дедуплицируем списки сохранения через LinkedHashSet, чтобы один и тот же
    // товар/значение атрибута не попадал в saveAll дважды — иначе при повторной
    // обработке одного товара возможен INSERT с нарушением unique_product_attribute.
    private final Set<Product> productsToSave = new LinkedHashSet<>();
    private final List<Attribute> attributesToSave = new ArrayList<>();
    private final Set<ProductAttributeValue> valuesToSave = new LinkedHashSet<>();

    // Сводки "было → стало" по каждому изменённому/новому товару —
    // только для отображения на странице результата импорта.
    private final List<ProductChangeSummary> changes = new ArrayList<>();

    /**
     * SKU товаров, которые были СОЗДАНЫ (а не обновлены) в рамках текущего импорта.
     * Используется для точной статистики новых/обновлённых товаров,
     * т.к. сравнение createdAt == updatedAt ненадёжно (разные вызовы Instant.now()).
     */
    private final Set<String> newSkus = new HashSet<>();

    /**
     * vendorCode, которые в текущем фиде встречаются у БОЛЕЕ ЧЕМ ОДНОГО оффера.
     * Для таких SKU нужно дизамбигировать резолвом vendorCode + '-' + offerId,
     * иначе два разных товара сошлись бы в один (см. stem: КАТ020, ШЛА014).
     */
    @Setter
    private Set<String> collidingVendorCodes = Set.of();

    /**
     * id пользователя, запустившего импорт. Проставляется в created_by_user_id
     * (новые товары) и updated_by_user_id (новые + изменённые товары), чтобы
     * в админке было видно, кто делал изменения при создании товара.
     */
    private final Long currentUserId;

    public ImportContext(
            Map<String, Category> categoryByXmlId,
            Map<String, Product> productsBySku,
            Map<String, Attribute> attributeCache,
            Map<String, ProductAttributeValue> valueCache,
            Long currentUserId
    ) {
        this.categoryByXmlId = categoryByXmlId;
        this.productsBySku = productsBySku;
        this.attributeCache = attributeCache;
        this.valueCache = valueCache;
        this.currentUserId = currentUserId;
    }
}