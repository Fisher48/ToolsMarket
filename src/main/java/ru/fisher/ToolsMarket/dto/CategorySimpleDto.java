package ru.fisher.ToolsMarket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategorySimpleDto {
    private Long id;
    private String title;
    private String name;
    private String description;
    private Integer sortOrder;
    private Instant createdAt;
}
