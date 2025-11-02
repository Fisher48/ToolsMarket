package ru.fisher.ToolsMarket.mapper;

import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import ru.fisher.ToolsMarket.dto.*;
import ru.fisher.ToolsMarket.models.Category;

import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryMapperService {

    private final ModelMapper modelMapper;

    public CategoryDto toDto(Category category) {
        if (category == null) return null;

        CategoryDto dto = modelMapper.map(category, CategoryDto.class);

        // Рекурсивно маппим родительскую категорию (только базовые поля)
        if (category.getParent() != null) {
            CategoryDto parentDto = modelMapper.map(category.getParent(), CategoryDto.class);
            // Чтобы избежать бесконечной рекурсии, не маппим родителя родителя и детей родителя
            parentDto.setParent(null);
            parentDto.setChildren(null);
            dto.setParent(parentDto);
        }

        // Маппим дочерние категории (только первый уровень)
        if (category.getChildren() != null) {
            dto.setChildren(category.getChildren().stream()
                    .map(child -> {
                        CategoryDto childDto = modelMapper.map(child, CategoryDto.class);
                        // Чтобы избежать рекурсии, не маппим детей детей
                        childDto.setChildren(null);
                        childDto.setParent(null); // Или можно оставить только parent ID
                        return childDto;
                    })
                    .collect(Collectors.toSet()));
        }

        return dto;
    }

    public CategorySimpleDto toSimpleDto(Category category) {
        if (category == null) return null;
        return modelMapper.map(category, CategorySimpleDto.class);
    }

    public CategoryWithParentDto toWithParentDto(Category category) {
        if (category == null) return null;

        CategoryWithParentDto dto = modelMapper.map(category, CategoryWithParentDto.class);

        // Маппим только простого родителя (без детей)
        if (category.getParent() != null) {
            dto.setParent(toSimpleDto(category.getParent()));
        }

        return dto;
    }

    public CategoryWithChildrenDto toWithChildrenDto(Category category) {
        if (category == null) return null;

        CategoryWithChildrenDto dto = modelMapper.map(category, CategoryWithChildrenDto.class);

        // Маппим только простых детей (без внуков)
        if (category.getChildren() != null) {
            dto.setChildren(category.getChildren().stream()
                    .map(this::toSimpleDto)
                    .collect(Collectors.toList()));
        }

        return dto;
    }

    public CategoryTreeDto toTreeDto(Category category) {
        if (category == null) return null;

        CategoryTreeDto dto = modelMapper.map(category, CategoryTreeDto.class);

        // Для дерева маппим только первый уровень детей
        if (category.getChildren() != null) {
            dto.setChildren(category.getChildren().stream()
                    .map(child -> {
                        CategoryTreeDto childDto = modelMapper.map(child, CategoryTreeDto.class);
                        // Не маппим детей детей, чтобы избежать рекурсии
                        childDto.setChildren(null);
                        return childDto;
                    })
                    .collect(Collectors.toList()));
        }

        return dto;
    }

    public Category toEntity(CategoryCreateDto dto) {
        return modelMapper.map(dto, Category.class);
    }
}
