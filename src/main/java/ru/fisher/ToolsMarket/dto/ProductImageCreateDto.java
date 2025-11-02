package ru.fisher.ToolsMarket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ProductImageCreateDto {
    @NotBlank(message = "URL изображения обязателен")
    private String url;

    private String alt;

    @NotNull(message = "Порядок сортировки обязателен")
    private Integer sortOrder = 0;

    @NotNull(message = "ID товара обязателен")
    private Long productId;
}
