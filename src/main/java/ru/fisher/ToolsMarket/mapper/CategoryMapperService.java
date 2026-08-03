package ru.fisher.ToolsMarket.mapper;

import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import ru.fisher.ToolsMarket.dto.CategoryDTO.CategoryAdminDto;
import ru.fisher.ToolsMarket.dto.CategoryDTO.CategoryDto;
import ru.fisher.ToolsMarket.dto.CategoryDTO.CategorySimpleDto;
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

        // Родитель - только ID и имя
        if (category.getParent() != null) {
            dto.setParentId(category.getParent().getId());
            dto.setParentName(category.getParent().getName());
            dto.setParentTitle(category.getParent().getTitle());
        }

        // Дети - простой DTO
        if (category.getChildren() != null && !category.getChildren().isEmpty()) {
            dto.setChildrenCount(category.getChildren().size());
            dto.setChildren(category.getChildren().stream()
                    .map(this::toSimpleDto)
                    .sorted(Comparator.comparing(CategorySimpleDto::getSortOrder)
                            .thenComparing(CategorySimpleDto::getName))
                    .toList());
        } else {
            dto.setChildrenCount(0);
            dto.setChildren(Collections.emptyList());
        }

        return dto;
    }

    public CategorySimpleDto toSimpleDto(Category category) {
        if (category == null) return null;
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

    public CategoryAdminDto convertToAdminDto(Category category, CategoryDto dto) {
        return CategoryAdminDto.builder()
                .id(category.getId())
                .name(category.getName())
                .title(category.getTitle())
                .description(category.getDescription())
                .parentName(dto.getParentName())
                .parentId(dto.getParentId())
                .sortOrder(category.getSortOrder())
                .imageUrl(category.getImageUrl())
                .thumbnailUrl(category.getThumbnailUrl())
                .childrenCount(dto.getChildrenCount())
                .attributesCount(category.getAttributes() != null ? category.getAttributes().size() : 0)
                .createdAt(category.getCreatedAt())
                .build();
    }

}
