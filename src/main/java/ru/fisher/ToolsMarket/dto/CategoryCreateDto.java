package ru.fisher.ToolsMarket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CategoryCreateDto {
    @NotBlank(message = "URL-адрес категории обязателен")
    @Size(max = 255, message = "URL-адрес не должен превышать 255 символов")
    private String title;

    @NotBlank(message = "Название категории обязательно")
    @Size(max = 255, message = "Название не должно превышать 255 символов")
    private String name;

    private String description;

    private Integer sortOrder = 0;

    private Long parentId;
}
