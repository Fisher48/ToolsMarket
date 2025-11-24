package ru.fisher.ToolsMarket.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.fisher.ToolsMarket.exceptions.ValidationException;
import ru.fisher.ToolsMarket.models.*;
import ru.fisher.ToolsMarket.repository.AttributeRepository;
import ru.fisher.ToolsMarket.repository.ProductAttributeValueRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttributeServiceTest {

    @Mock
    private AttributeRepository attributeRepository;

    @Mock
    private ProductAttributeValueRepository valueRepository;

    @InjectMocks
    private AttributeService attributeService;

    private Category category;
    private Product product;
    private Attribute requiredAttribute;
    private Attribute optionalAttribute;

    @BeforeEach
    void setUp() {
        category = Category.builder()
                .id(1L)
                .name("Электроинструменты")
                .build();

        product = Product.builder()
                .id(1L)
                .name("Дрель PRO")
                .price(new BigDecimal("15000.00"))
                .categories(Set.of(category))
                .build();

        requiredAttribute = Attribute.builder()
                .id(1L)
                .name("Мощность")
                .type(AttributeType.NUMBER)
                .unit("Вт")
                .required(true)
                .category(category)
                .sortOrder(1)
                .build();

        optionalAttribute = Attribute.builder()
                .id(2L)
                .name("Цвет")
                .type(AttributeType.STRING)
                .required(false)
                .category(category)
                .sortOrder(2)
                .build();
    }

    @Test
    void saveProductAttributes_WithValidData_ShouldSaveValues() {
        // Arrange
        Map<Long, String> attributeValues = Map.of(
                1L, "850",
                2L, "Черный"
        );

        when(attributeRepository.findByCategoryIdAndRequiredTrue(1L))
                .thenReturn(List.of(requiredAttribute));
        when(attributeRepository.findById(1L)).thenReturn(Optional.of(requiredAttribute));
        when(attributeRepository.findById(2L)).thenReturn(Optional.of(optionalAttribute));

        // Act
        attributeService.saveProductAttributes(product, attributeValues);

        // Assert
        verify(valueRepository).deleteByProductId(1L);
        verify(valueRepository, times(2)).save(any(ProductAttributeValue.class));
    }

    @Test
    void saveProductAttributes_WithMissingRequiredAttribute_ShouldThrowException() {
        // Arrange
        Map<Long, String> attributeValues = Map.of(2L, "Черный"); // Только опциональный атрибут

        when(attributeRepository.findByCategoryIdAndRequiredTrue(1L))
                .thenReturn(List.of(requiredAttribute));

        // Act & Assert
        ValidationException exception = assertThrows(ValidationException.class,
                () -> attributeService.saveProductAttributes(product, attributeValues));

        assertEquals("Обязательный атрибут 'Мощность' не заполнен", exception.getMessage());
        verify(valueRepository, never()).deleteByProductId(any());
        verify(valueRepository, never()).save(any());
    }

    @Test
    void saveProductAttributes_WithEmptyRequiredValue_ShouldThrowException() {
        // Arrange
        Map<Long, String> attributeValues = Map.of(1L, ""); // Пустое значение для обязательного атрибута

        when(attributeRepository.findByCategoryIdAndRequiredTrue(1L))
                .thenReturn(List.of(requiredAttribute));

        // Act & Assert
        ValidationException exception = assertThrows(ValidationException.class,
                () -> attributeService.saveProductAttributes(product, attributeValues));

        assertEquals("Обязательный атрибут 'Мощность' не заполнен", exception.getMessage());
    }

    @Test
    void getProductAttributes_ShouldReturnAttributesMap() {
        // Arrange
        ProductAttributeValue value1 = ProductAttributeValue.builder()
                .attribute(requiredAttribute)
                .value("850")
                .build();

        ProductAttributeValue value2 = ProductAttributeValue.builder()
                .attribute(optionalAttribute)
                .value("Черный")
                .build();

        when(valueRepository.findByProductIdOrderBySortOrder(1L))
                .thenReturn(List.of(value1, value2));

        // Act
        Map<Attribute, String> result = attributeService.getProductAttributes(product);

        // Assert
        assertEquals(2, result.size());
        assertEquals("850", result.get(requiredAttribute));
        assertEquals("Черный", result.get(optionalAttribute));
    }

    @Test
    void getAttributesForProduct_WithMultipleCategories_ShouldReturnAllAttributes() {
        // Arrange
        Category category2 = Category.builder().id(2L).name("Дрели").build();
        product.setCategories(Set.of(category, category2));

        Attribute attribute2 = Attribute.builder()
                .id(3L)
                .name("Тип патрона")
                .category(category2)
                .build();

        when(attributeRepository.findByCategoryIds(List.of(1L, 2L)))
                .thenReturn(List.of(requiredAttribute, optionalAttribute, attribute2));

        // Act
        List<Attribute> result = attributeService.getAttributesForProduct(product);

        // Assert
        assertEquals(3, result.size());
        assertTrue(result.contains(requiredAttribute));
        assertTrue(result.contains(optionalAttribute));
        assertTrue(result.contains(attribute2));
    }

    @Test
    void deleteAttribute_ShouldDeleteValuesAndAttribute() {
        // Act
        attributeService.delete(1L);

        // Assert
        verify(valueRepository).deleteByAttributeId(1L);
        verify(attributeRepository).deleteById(1L);
    }

    @Test
    void getFilterOptionsForCategories_ShouldReturnDistinctValues() {
        // Arrange
        List<Long> categoryIds = List.of(1L, 2L);

        Attribute filterableAttribute = Attribute.builder()
                .id(1L)
                .name("Цвет")
                .filterable(true)
                .build();

        when(attributeRepository.findByCategoryIds(categoryIds))
                .thenReturn(List.of(filterableAttribute));
        when(valueRepository.findDistinctValuesByAttributeId(1L))
                .thenReturn(List.of("Черный", "Красный", "Синий"));

        // Act
        Map<Long, List<String>> result = attributeService.getFilterOptionsForCategories(categoryIds);

        // Assert
        assertEquals(1, result.size());
        assertEquals(List.of("Черный", "Красный", "Синий"), result.get(1L));
    }
}