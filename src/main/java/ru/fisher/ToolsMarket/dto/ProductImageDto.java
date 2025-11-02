package ru.fisher.ToolsMarket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductImageDto {
    private Long id;
    private String originalFileName;
    private String url;
    private String alt;
    private Integer sortOrder;
    private boolean main;
}
