package ru.fisher.ToolsMarket.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Set;

@Data
@Builder
public class CategoryAdminDto {
    private Long id;
    private String name;
    private String title;
    private String description;
    private String parentName;
    private Long parentId;
    private Integer sortOrder;
    private String imageUrl;
    private String thumbnailUrl;
    private int childrenCount;
    private int attributesCount;
    private Instant createdAt;
    private Set<CategoryAdminDto> children;
}
