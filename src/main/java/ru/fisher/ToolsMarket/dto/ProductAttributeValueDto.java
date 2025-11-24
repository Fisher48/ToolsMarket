package ru.fisher.ToolsMarket.dto;

import lombok.*;
import ru.fisher.ToolsMarket.models.AttributeType;
import ru.fisher.ToolsMarket.models.ProductAttributeValue;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductAttributeValueDto {
    private Long id;
    private String attributeName;
    private String attributeUnit;
    private AttributeType attributeType;
    private String value;
    private Integer sortOrder;

    public static ProductAttributeValueDto fromEntity(ProductAttributeValue value) {
        return ProductAttributeValueDto.builder()
                .id(value.getId())
                .attributeName(value.getAttribute().getName())
                .attributeUnit(value.getAttribute().getUnit())
                .attributeType(value.getAttribute().getType())
                .value(value.getValue())
                .sortOrder(value.getSortOrder())
                .build();
    }
}
