package ru.fisher.ToolsMarket.dto;

import org.springframework.data.jpa.domain.Specification;
import ru.fisher.ToolsMarket.models.Category;

public class CategorySpecification {

    // Фильтр по названию
    public static Specification<Category> nameLike(String name) {
        return (root, query, cb) ->
                cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%");
    }

    // Фильтр по URL (title)
    public static Specification<Category> titleLike(String title) {
        return (root, query, cb) ->
                cb.like(cb.lower(root.get("title")), "%" + title.toLowerCase() + "%");
    }

    // Фильтр по родительской категории
    public static Specification<Category> hasParent(Long parentId) {
        if (parentId == null) {
            return (root, query, cb) -> cb.isNull(root.get("parent"));
        }
        return (root, query, cb) -> cb.equal(root.get("parent").get("id"), parentId);
    }

    // Фильтр по ID категории
    public static Specification<Category> idEquals(Long id) {
        return (root, query, cb) -> cb.equal(root.get("id"), id);
    }

    // Поиск по всем полям
    public static Specification<Category> searchAll(String query) {
        String likePattern = "%" + query.toLowerCase() + "%";
        return (root, q, cb) -> cb.or(
                cb.like(cb.lower(root.get("name")), likePattern),
                cb.like(cb.lower(root.get("title")), likePattern)
        );
    }
}
