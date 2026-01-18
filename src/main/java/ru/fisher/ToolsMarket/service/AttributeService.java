package ru.fisher.ToolsMarket.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.fisher.ToolsMarket.exceptions.ValidationException;
import ru.fisher.ToolsMarket.models.Attribute;
import ru.fisher.ToolsMarket.models.Category;
import ru.fisher.ToolsMarket.models.Product;
import ru.fisher.ToolsMarket.models.ProductAttributeValue;
import ru.fisher.ToolsMarket.repository.AttributeRepository;
import ru.fisher.ToolsMarket.repository.ProductAttributeValueRepository;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class AttributeService {
    private final AttributeRepository attributeRepository;
    private final ProductAttributeValueRepository valueRepository;

    public Optional<Attribute> findById(Long id) {
        return attributeRepository.findById(id);
    }

    public Attribute save(Attribute attribute) {
        return attributeRepository.save(attribute);
    }

    public void delete(Long id) {
        // Сначала удаляем значения атрибутов у товаров
        valueRepository.deleteByAttributeId(id);
        // Затем удаляем сам атрибут
        attributeRepository.deleteById(id);
    }

    public List<Attribute> getAttributesByCategory(Long categoryId) {
        return attributeRepository.findByCategoryIdOrderBySortOrder(categoryId);
    }

    public List<Attribute> getFilterableAttributesByCategory(Long categoryId) {
        return attributeRepository.findByCategoryIdAndFilterableTrueOrderBySortOrder(categoryId);
    }

    public List<Attribute> getRequiredAttributesByCategory(Long categoryId) {
        return attributeRepository.findByCategoryIdAndRequiredTrue(categoryId);
    }

    @Transactional
    public void saveProductAttributes(Product product, Map<Long, String> attributeValues) {
        log.info("Saving attributes for product {}: {}", product.getId(), attributeValues);

        // Проверяем обязательные атрибуты
        List<Attribute> requiredAttributes = getRequiredAttributesForProduct(product);
        for (Attribute requiredAttr : requiredAttributes) {
            if (!attributeValues.containsKey(requiredAttr.getId()) ||
                    attributeValues.get(requiredAttr.getId()) == null ||
                    attributeValues.get(requiredAttr.getId()).trim().isEmpty()) {
                throw new ValidationException("Обязательный атрибут '" + requiredAttr.getName() + "' не заполнен");
            }
        }

        // Удаляем старые значения
        valueRepository.deleteByProductId(product.getId());

        // Сохраняем новые значения
        for (Map.Entry<Long, String> entry : attributeValues.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().trim().isEmpty()) {
                Attribute attribute = attributeRepository.findById(entry.getKey())
                        .orElseThrow(() -> new RuntimeException("Attribute not found: " + entry.getKey()));

                ProductAttributeValue value = ProductAttributeValue.builder()
                        .product(product)
                        .attribute(attribute)
                        .value(entry.getValue().trim())
                        .sortOrder(attribute.getSortOrder())
                        .build();

                valueRepository.save(value);
                log.info("Saved attribute value: {} = {}", attribute.getName(), entry.getValue());
            }
        }
    }

    public Map<Attribute, String> getProductAttributes(Product product) {
        return valueRepository.findByProductIdOrderBySortOrder(product.getId()).stream()
                .collect(Collectors.toMap(
                        ProductAttributeValue::getAttribute,
                        ProductAttributeValue::getValue,
                        (v1, v2) -> v1, // merge function - берем первое значение при дубликатах
                        LinkedHashMap::new // сохраняем порядок сортировки
                ));
    }

    public List<ProductAttributeValue> getProductAttributeValues(Product product) {
        return valueRepository.findByProductIdOrderBySortOrder(product.getId());
    }

    public List<Attribute> getAttributesForProduct(Product product) {
        Set<Long> categoryIds = product.getCategories().stream()
                .map(Category::getId)
                .collect(Collectors.toSet());

        return attributeRepository.findByCategoryIds(new ArrayList<>(categoryIds));
    }

    private List<Attribute> getRequiredAttributesForProduct(Product product) {
        Set<Long> categoryIds = product.getCategories().stream()
                .map(Category::getId)
                .collect(Collectors.toSet());

        return categoryIds.stream()
                .flatMap(categoryId -> attributeRepository.findByCategoryIdAndRequiredTrue(categoryId).stream())
                .collect(Collectors.toList());
    }

    public Map<Long, List<String>> getFilterOptionsForCategories(List<Long> categoryIds) {
        List<Attribute> filterableAttributes = attributeRepository.findByCategoryIds(categoryIds).stream()
                .filter(Attribute::isFilterable)
                .toList();

        Map<Long, List<String>> filterOptions = new HashMap<>();

        for (Attribute attribute : filterableAttributes) {
            List<String> distinctValues = valueRepository.findDistinctValuesByAttributeId(attribute.getId());
            filterOptions.put(attribute.getId(), distinctValues);
        }

        return filterOptions;
    }
}
