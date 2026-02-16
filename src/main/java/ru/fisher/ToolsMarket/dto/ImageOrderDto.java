package ru.fisher.ToolsMarket.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImageOrderDto {
    private Long id;
    private Integer sortOrder;
}
