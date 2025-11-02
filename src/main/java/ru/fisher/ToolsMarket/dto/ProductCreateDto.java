package ru.fisher.ToolsMarket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Set;

@Data
public class ProductCreateDto {
    @NotBlank(message = "Название товара обязательно")
    @Size(max = 512, message = "Название не должно превышать 512 символов")
    private String name;

    @NotBlank(message = "URL-адрес товара обязателен")
    @Size(max = 512, message = "URL-адрес не должен превышать 512 символов")
    private String title;

    @Size(max = 1024, message = "Краткое описание не должно превышать 1024 символов")
    private String shortDescription;

    private String description;

    @Size(max = 100, message = "Артикул не должен превышать 100 символов")
    private String sku;

    @NotNull(message = "Цена обязательна")
    @Positive(message = "Цена должна быть положительной")
    private BigDecimal price;

    private String currency = "RUB";

    private boolean active = true;

    private Set<Long> categoryIds;
}
