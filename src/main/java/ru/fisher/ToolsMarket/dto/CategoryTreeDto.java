package ru.fisher.ToolsMarket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryTreeDto {
    private Long id;
    private String name;
    private String title;
    private String description;
    private List<CategoryTreeDto> children;
    private int productsCount;

    public boolean isExpandable() {
        return children != null && !children.isEmpty();
    }
}
