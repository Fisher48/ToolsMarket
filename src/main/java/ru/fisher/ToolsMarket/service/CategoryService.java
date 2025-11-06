package ru.fisher.ToolsMarket.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.fisher.ToolsMarket.dto.*;
import ru.fisher.ToolsMarket.mapper.CategoryMapperService;
import ru.fisher.ToolsMarket.models.Category;
import ru.fisher.ToolsMarket.repository.CategoryRepository;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapperService categoryMapperService;


    // Методы для админки
    public List<Category> findAllEntities() {
        return categoryRepository.findAll();
    }

    public Optional<Category> findEntityById(Long id) {
        return categoryRepository.findById(id);
    }

    @Transactional
    public Category saveEntity(Category category) {
        return categoryRepository.save(category);
    }

    @Transactional
    public void deleteEntity(Long id) {
        categoryRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<Category> findByIds(List<Long> ids) {
        return categoryRepository.findAllById(ids);
    }

    public List<CategoryDto> getRootCategories() {
        return categoryRepository.findByParentIsNullOrderBySortOrderAsc().stream()
                .map(categoryMapperService::toDto) // Используем простой DTO
                .toList();
    }

//    public List<Category> getParentCategories() {
//        return categoryRepository.findByParentIsNullOrderBySortOrderAsc();
//    }

//    public List<CategoryWithChildrenDto> getRootCategoriesWithChildren() {
//        return categoryRepository.findByParentIsNullOrderBySortOrderAsc().stream()
//                .map(categoryMapperService::toWithChildrenDto)
//                .toList();
//    }

//    public List<CategoryTreeDto> getCategoryTree() {
//        return categoryRepository.findByParentIsNullOrderBySortOrderAsc().stream()
//                .map(categoryMapperService::toTreeDto)
//                .toList();
//    }

    public Optional<CategoryDto> findByTitle(String title) {
        return categoryRepository.findByTitle(title)
                .map(categoryMapperService::toDto);
    }

    public Optional<CategoryDto> findById(Long id) {
        return categoryRepository.findById(id)
                .map(categoryMapperService::toDto);
    }

//    public Optional<CategorySimpleDto> findSimpleById(Long id) {
//        return categoryRepository.findById(id)
//                .map(categoryMapperService::toSimpleDto);
//    }

    public List<CategoryDto> findAll() {
        return categoryRepository.findAll().stream()
                .map(categoryMapperService::toDto)
                .toList();
    }

    public List<Category> findAllCategories() {
        return categoryRepository.findAll();
    }

//    @Transactional
//    public CategoryDto update(Long id, CategoryUpdateDto categoryDto) {
//        Category category = categoryRepository.findById(id)
//                .orElseThrow(() -> new RuntimeException("Category not found"));
//
//        // Обновляем поля
//        if (categoryDto.getTitle() != null) category.setTitle(categoryDto.getTitle());
//        if (categoryDto.getName() != null) category.setName(categoryDto.getName());
//        if (categoryDto.getDescription() != null) category.setDescription(categoryDto.getDescription());
//        if (categoryDto.getSortOrder() != null) category.setSortOrder(categoryDto.getSortOrder());
//
//        // Обновляем родителя
//        if (categoryDto.getParentId() != null) {
//            Category parent = categoryRepository.findById(categoryDto.getParentId())
//                    .orElseThrow(() -> new RuntimeException("Parent category not found"));
//            category.setParent(parent);
//        } else {
//            category.setParent(null);
//        }
//
//        Category updated = categoryRepository.save(category);
//        return categoryMapperService.toDto(updated);
//    }

//    @Transactional
//    public CategoryDto save(CategoryCreateDto categoryDto) {
//        Category category = categoryMapperService.toEntity(categoryDto);
//
//        // Установка родительской категории
//        if (categoryDto.getParentId() != null) {
//            Category parent = categoryRepository.findById(categoryDto.getParentId())
//                    .orElseThrow(() -> new RuntimeException("Parent category not found"));
//            category.setParent(parent);
//        }
//
//        Category saved = categoryRepository.save(category);
//        return categoryMapperService.toDto(saved);
//    }

    @Transactional
    public void delete(Long id) {
        categoryRepository.deleteById(id);
    }
}
