package ru.fisher.ToolsMarket.mapper;

import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import ru.fisher.ToolsMarket.dto.*;
import ru.fisher.ToolsMarket.models.Category;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryMapperService {

    private final ModelMapper modelMapper;

    public CategoryDto toDto(Category category) {
        if (category == null) return null;

        // Создаем DTO вручную, чтобы избежать рекурсии
        CategoryDto dto = CategoryDto.builder()
                .id(category.getId())
                .title(category.getTitle())
                .name(category.getName())
                .description(category.getDescription())
                .sortOrder(category.getSortOrder())
                .imageUrl(category.getImageUrl())
                .thumbnailUrl(category.getThumbnailUrl())
                .createdAt(category.getCreatedAt())
                .build();

        // Родитель - только ID и имя (НЕ объект)
        if (category.getParent() != null) {
            dto.setParentId(category.getParent().getId());
            dto.setParentName(category.getParent().getName());
            // НЕ создаем объект parentDto!
        }

        // Дети - только простой DTO (НЕ полный CategoryDto)
        if (category.getChildren() != null && !category.getChildren().isEmpty()) {
            dto.setChildrenCount(category.getChildren().size());
            dto.setChildren(category.getChildren().stream()
                    .map(this::toSimpleDto) // Используем toSimpleDto а не toDto
                    .sorted(Comparator.comparing(CategorySimpleDto::getSortOrder))
                    .collect(Collectors.toList()));
        } else {
            dto.setChildrenCount(0);
            dto.setChildren(Collections.emptyList());
        }

        return dto;
    }

    public CategorySimpleDto toSimpleDto(Category category) {
        if (category == null) return null;

        // Для простого DTO можно использовать ModelMapper
        return modelMapper.map(category, CategorySimpleDto.class);
    }

    // Дополнительные методы для коллекций
    public List<CategoryDto> toDtoList(List<Category> categories) {
        return categories.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public List<CategorySimpleDto> toSimpleDtoList(List<Category> categories) {
        return categories.stream()
                .map(this::toSimpleDto)
                .collect(Collectors.toList());
    }

//    public CategoryWithParentDto toWithParentDto(Category category) {
//        if (category == null) return null;
//
//        CategoryWithParentDto dto = modelMapper.map(category, CategoryWithParentDto.class);
//
//        // Маппим только простого родителя (без детей)
//        if (category.getParent() != null) {
//            dto.setParent(toSimpleDto(category.getParent()));
//        }
//
//        return dto;
//    }

//    public CategoryWithChildrenDto toWithChildrenDto(Category category) {
//        if (category == null) return null;
//
//        CategoryWithChildrenDto dto = modelMapper.map(category, CategoryWithChildrenDto.class);
//
//        // Маппим только простых детей (без внуков)
//        if (category.getChildren() != null) {
//            dto.setChildren(category.getChildren().stream()
//                    .map(this::toSimpleDto)
//                    .collect(Collectors.toList()));
//        }
//
//        return dto;
//    }

//    public CategoryTreeDto toTreeDto(Category category) {
//        if (category == null) return null;
//
//        CategoryTreeDto dto = modelMapper.map(category, CategoryTreeDto.class);
//
//        // Для дерева маппим только первый уровень детей
//        if (category.getChildren() != null) {
//            dto.setChildren(category.getChildren().stream()
//                    .map(child -> {
//                        CategoryTreeDto childDto = modelMapper.map(child, CategoryTreeDto.class);
//                        // Не маппим детей детей, чтобы избежать рекурсии
//                        childDto.setChildren(null);
//                        return childDto;
//                    })
//                    .collect(Collectors.toList()));
//        }
//
//        return dto;
//    }

//    public Category toEntity(CategoryCreateDto dto) {
//        return modelMapper.map(dto, Category.class);
//    }

}
