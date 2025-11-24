package ru.fisher.ToolsMarket.dto;

import lombok.*;
import ru.fisher.ToolsMarket.models.Attribute;
import ru.fisher.ToolsMarket.models.AttributeType;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttributeDto {
    private Long id;
    private String name;
    private String unit;
    private AttributeType type;
    private Integer sortOrder;
    private String options;
    private boolean required;
    private boolean filterable;
    private Long categoryId;
    private String categoryName;

    public static AttributeDto fromEntity(Attribute attribute) {
        return AttributeDto.builder()
                .id(attribute.getId())
                .name(attribute.getName())
                .unit(attribute.getUnit())
                .type(attribute.getType())
                .sortOrder(attribute.getSortOrder())
                .options(attribute.getOptions())
                .required(attribute.isRequired())
                .filterable(attribute.isFilterable())
                .categoryId(attribute.getCategory().getId())
                .categoryName(attribute.getCategory().getName())
                .build();
    }
}
