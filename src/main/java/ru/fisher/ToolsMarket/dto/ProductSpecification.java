package ru.fisher.ToolsMarket.dto;

import org.springframework.data.jpa.domain.Specification;
import ru.fisher.ToolsMarket.models.Product;

import java.math.BigDecimal;

public class ProductSpecification {

    // Фильтр по названию
    public static Specification<Product> nameLike(String name) {
        return (root, query, cb) ->
                cb.like(cb.lower(root.get("name")),
                        "%" + name.toLowerCase() + "%");
    }

    // Фильтр по минимальной цене
    public static Specification<Product> minPrice(BigDecimal min) {
        return (root, query, cb) ->
                cb.greaterThanOrEqualTo(root.get("price"), min);
    }

    // Фильтр по максимальной цене
    public static Specification<Product> maxPrice(BigDecimal max) {
        return (root, query, cb) ->
                cb.lessThanOrEqualTo(root.get("price"), max);
    }

    // Фильтр по артикулу
    public static Specification<Product> skuLike(String sku) {
        return (root, query, cb) ->
                cb.like(cb.lower(root.get("sku")), "%" + sku.toLowerCase() + "%");
    }

    // Фильтр по статусу
    public static Specification<Product> hasStatus(Boolean active) {
        return (root, query, cb) ->
                cb.equal(root.get("active"), active);
    }

    // Фильтр по категории
    public static Specification<Product> hasCategory(Long categoryId) {
        return (root, query, cb) ->
                cb.equal(root.join("categories").get("id"), categoryId);
    }

    // Поиск по всем полям (название + артикул + описание)
    public static Specification<Product> searchAll(String query) {
        String likePattern = "%" + query.toLowerCase() + "%";
        return (root, q, cb) -> cb.or(
                cb.like(cb.lower(root.get("name")), likePattern),
                cb.like(cb.lower(root.get("sku")), likePattern),
                cb.like(cb.lower(root.get("shortDescription")), likePattern),
                cb.like(cb.lower(root.get("description")), likePattern)
        );
    }
}
