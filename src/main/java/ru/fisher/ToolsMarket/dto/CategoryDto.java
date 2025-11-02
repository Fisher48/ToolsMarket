package ru.fisher.ToolsMarket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Set;

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
    private CategoryDto parent;
    private Set<CategoryDto> children;
    private Instant createdAt;

    // Дополнительные вычисляемые поля
    public boolean hasParent() {
        return parent != null;
    }

    public boolean hasChildren() {
        return children != null && !children.isEmpty();
    }

    public int getProductsCount() {
        // Можно добавить логику подсчета товаров
        return 0;
    }
}
