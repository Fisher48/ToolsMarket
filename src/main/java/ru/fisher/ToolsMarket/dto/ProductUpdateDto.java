package ru.fisher.ToolsMarket.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Set;

@Data
public class ProductUpdateDto {
    @Size(max = 512, message = "Название не должно превышать 512 символов")
    private String name;

    @Size(max = 512, message = "URL-адрес не должен превышать 512 символов")
    private String title;

    @Size(max = 1024, message = "Краткое описание не должно превышать 1024 символов")
    private String shortDescription;

    private String description;

    @Size(max = 100, message = "Артикул не должен превышать 100 символов")
    private String sku;

    private BigDecimal price;

    private String currency;

    private Boolean active;

    private Set<Long> categoryIds;
}
