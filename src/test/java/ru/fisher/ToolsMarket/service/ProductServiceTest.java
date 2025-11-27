package ru.fisher.ToolsMarket.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ContextConfiguration;
import ru.fisher.ToolsMarket.PostgresTestConfig;
import ru.fisher.ToolsMarket.dto.ProductCreateDto;
import ru.fisher.ToolsMarket.dto.ProductDto;
import ru.fisher.ToolsMarket.dto.ProductUpdateDto;
import ru.fisher.ToolsMarket.mapper.ProductMapperService;
import ru.fisher.ToolsMarket.models.Category;
import ru.fisher.ToolsMarket.models.Product;
import ru.fisher.ToolsMarket.repository.CategoryRepository;
import ru.fisher.ToolsMarket.repository.ProductRepository;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@ContextConfiguration(initializers = PostgresTestConfig.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductMapperService productMapperService;

    @Mock
    private AttributeService attributeService;

    @InjectMocks
    private ProductService productService;

    @Test
    void saveWithAttributes_ShouldSaveProductAndAttributes() {
        // Arrange
        Product product = Product.builder()
                .name("Дрель PRO")
                .price(new BigDecimal("15000.00"))
                .build();

        Map<Long, String> attributeValues = Map.of(
                1L, "850",
                2L, "Черный"
        );

        Product savedProduct = Product.builder()
                .id(1L)
                .name("Дрель PRO")
                .price(new BigDecimal("15000.00"))
                .build();

        when(productRepository.save(product)).thenReturn(savedProduct);

        // Act
        Product result = productService.saveWithAttributes(product, attributeValues);

        // Assert
        verify(productRepository).save(product);
        verify(attributeService).saveProductAttributes(savedProduct, attributeValues);
        assertEquals(savedProduct, result);
    }

    @Test
    void saveWithAttributes_WithNullAttributes_ShouldOnlySaveProduct() {
        // Arrange
        Product product = Product.builder()
                .name("Дрель PRO")
                .price(new BigDecimal("15000.00"))
                .build();

        Product savedProduct = Product.builder()
                .id(1L)
                .name("Дрель PRO")
                .price(new BigDecimal("15000.00"))
                .build();

        when(productRepository.save(product)).thenReturn(savedProduct);

        // Act
        Product result = productService.saveWithAttributes(product, null);

        // Assert
        verify(productRepository).save(product);
        verify(attributeService, never()).saveProductAttributes(any(), any());
        assertEquals(savedProduct, result);
    }

    @Test
    void save_WithCategories_ShouldSetCategoriesAndSave() {
        // Arrange
        ProductCreateDto dto = new ProductCreateDto();
        dto.setName("Дрель PRO");
        dto.setPrice(new BigDecimal("15000.00"));
        dto.setCategoryIds(List.of(1L, 2L));

        Category category1 = Category.builder().id(1L).name("Электроинструменты").build();
        Category category2 = Category.builder().id(2L).name("Дрели").build();

        Product product = Product.builder()
                .name("Дрель PRO")
                .price(new BigDecimal("15000.00"))
                .categories(new HashSet<>())
                .build();

        Product savedProduct = Product.builder()
                .id(1L)
                .name("Дрель PRO")
                .price(new BigDecimal("15000.00"))
                .categories(Set.of(category1, category2))
                .build();

        ProductDto expectedDto = new ProductDto();

        when(productMapperService.toEntity(dto)).thenReturn(product);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category1));
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(category2));
        when(productRepository.save(any(Product.class))).thenReturn(savedProduct);
        when(productMapperService.toDto(savedProduct)).thenReturn(expectedDto);

        // Act
        ProductDto result = productService.save(dto);

        // Assert
        assertNotNull(result);
        verify(categoryRepository).findById(1L);
        verify(categoryRepository).findById(2L);
        verify(productRepository).save(product);
        assertEquals(2, product.getCategories().size());
    }

    @Test
    void update_ShouldUpdateProductAndCategories() {
        // Arrange
        Long productId = 1L;
        ProductUpdateDto dto = new ProductUpdateDto();
        dto.setName("Дрель PRO Updated");
        dto.setPrice(new BigDecimal("16000.00"));
        dto.setCategoryIds(List.of(1L, 3L));

        Product existingProduct = Product.builder()
                .id(productId)
                .name("Дрель PRO")
                .price(new BigDecimal("15000.00"))
                .categories(new HashSet<>())
                .build();

        Category category1 = Category.builder().id(1L).name("Электроинструменты").build();
        Category category3 = Category.builder().id(3L).name("Профессиональные").build();

        Product updatedProduct = Product.builder()
                .id(productId)
                .name("Дрель PRO Updated")
                .price(new BigDecimal("16000.00"))
                .categories(Set.of(category1, category3))
                .build();

        ProductDto expectedDto = new ProductDto();

        when(productRepository.findById(productId)).thenReturn(Optional.of(existingProduct));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category1));
        when(categoryRepository.findById(3L)).thenReturn(Optional.of(category3));
        when(productRepository.save(existingProduct)).thenReturn(updatedProduct);
        when(productMapperService.toDto(updatedProduct)).thenReturn(expectedDto);

        // Act
        ProductDto result = productService.update(productId, dto);

        // Assert
        assertNotNull(result);
        verify(productMapperService).updateEntityFromDto(dto, existingProduct);
        verify(productRepository).save(existingProduct);
        assertEquals(2, existingProduct.getCategories().size());
    }
}