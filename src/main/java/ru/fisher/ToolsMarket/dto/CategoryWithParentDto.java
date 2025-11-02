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
public class CategoryWithParentDto {
    private Long id;
    private String title;
    private String name;
    private String description;
    private Integer sortOrder;
    private CategorySimpleDto parent;
    private List<CategorySimpleDto> children;
    private Instant createdAt;
}
