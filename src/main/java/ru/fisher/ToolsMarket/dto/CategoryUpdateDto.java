package ru.fisher.ToolsMarket.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryUpdateDto {
    @NotBlank(message = "Название обязательно")
    private String title;
    private String name;
    private String description;
    private Integer sortOrder;
    private Long parentId;
}
