package ru.fisher.ToolsMarket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryDto {
    private Long id;
    private String title;
    private String name;
    private String description;
    private Integer sortOrder;
    private String imageUrl;        // Добавьте это поле
    private String thumbnailUrl;
//    private CategoryDto parent;
//    private Set<CategoryDto> children;
// ПРОСТЫЕ поля вместо рекурсивных объектов
    private Long parentId;
    private String parentName;
    private String parentTitle;
    private int childrenCount;
    private List<CategorySimpleDto> children; // простой DTO без рекурсии
    private Instant createdAt;

    // Дополнительные вычисляемые поля
    public boolean hasParent() {
        return parentId != null;
    }

    public boolean hasChildren() {
        return children != null && !children.isEmpty();
    }

    public int getProductsCount() {
        // Можно добавить логику подсчета товаров
        return 0;
    }
}
