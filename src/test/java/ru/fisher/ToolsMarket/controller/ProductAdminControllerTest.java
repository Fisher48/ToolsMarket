package ru.fisher.ToolsMarket.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.fisher.ToolsMarket.PostgresTestConfig;
import ru.fisher.ToolsMarket.dto.ProductDTO.ProductAdminDto;
import ru.fisher.ToolsMarket.models.*;
import ru.fisher.ToolsMarket.service.AttributeService;
import ru.fisher.ToolsMarket.service.CategoryService;
import ru.fisher.ToolsMarket.service.ImageStorageService;
import ru.fisher.ToolsMarket.service.ProductService;
import ru.fisher.ToolsMarket.service.UserService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasKey;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(initializers = PostgresTestConfig.class)
class ProductAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ProductService productService;
    @MockitoBean
    private CategoryService categoryService;
    @MockitoBean
    private ImageStorageService imageStorageService;
    @MockitoBean
    private AttributeService attributeService;
    @MockitoBean
    private UserService userService;

    private void stubAdminUser() {
        when(userService.findByUsername("testuser"))
                .thenReturn(Optional.of(User.builder().id(1L).username("testuser").build()));
    }

    private Product createTestProductWithDetails() {
        Category category = Category.builder()
                .id(1L)
                .name("Электроинструменты")
                .attributes(new HashSet<>())
                .build();

        Attribute attribute = Attribute.builder()
                .id(1L)
                .name("Мощность")
                .category(category)
                .build();

        category.setAttributes(Set.of(attribute));

        ProductAttributeValue attributeValue = ProductAttributeValue.builder()
                .id(1L)
                .attribute(attribute)
                .value("850")
                .build();

        return Product.builder()
                .id(1L)
                .name("Test Product")
                .title("test-product")
                .sku("TEST001")
                .price(new BigDecimal("999.99"))
                .currency("RUB")
                .shortDescription("Short description")
                .description("Full description")
                .active(true)
                .attributeValues(Set.of(attributeValue))
                .categories(new HashSet<>())
                .images(new HashSet<>())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private ProductImage createTestProductImage() {
        return ProductImage.builder()
                .id(1L)
                .url("http://localhost:8080/images/test_product_123.jpg")
                .alt("Test Image")
                .sortOrder(0)
                .build();
    }

    private Category createTestCategory() {
        return Category.builder()
                .id(1L)
                .name("Test Category")
                .title("test-category")
                .build();
    }

    @Test
    @WithMockUser(username = "testuser", roles = "ADMIN")
    void index_ShouldReturnProductsPage() throws Exception {
        // Given
        ProductAdminDto dto = new ProductAdminDto(1L, "Test Product", "test-product",
                "TEST001", new BigDecimal("999.99"), true, "Обычный",
                List.of(), Instant.now(), null, null, null, null);
        Page<ProductAdminDto> page = new PageImpl<>(List.of(dto));

        when(productService.search(any(), any(), any(), any(), any(), any(), any())).thenReturn(page);
        when(categoryService.findAllCategories()).thenReturn(List.of());

        // When & Then
        mockMvc.perform(get("/admin/products").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/products/index"))
                .andExpect(model().attributeExists("productPage", "categories", "currentSort"))
                .andExpect(model().attribute("productPage", page));
    }

    @Test
    @WithMockUser(username = "testuser", roles = "ADMIN")
    void show_WhenProductExists_ShouldReturnProductView() throws Exception {
        // Given
        Product product = createTestProductWithDetails();
        Map<Attribute, String> mockAttributes = Map.of();

        when(productService.findByIdWithAllRelations(1L)).thenReturn(Optional.of(product));
        when(attributeService.getProductAttributes(product)).thenReturn(mockAttributes);

        // When & Then
        mockMvc.perform(get("/admin/products/1").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/products/show"))
                .andExpect(model().attributeExists("product", "productAttributes"))
                .andExpect(model().attribute("product", product))
                .andExpect(model().attribute("productAttributes", mockAttributes));
    }

    @Test
    @WithMockUser(username = "testuser", roles = "ADMIN")
    void show_WhenProductNotExists_ShouldReturnNotFound() throws Exception {
        // Given
        when(productService.findByIdWithAllRelations(1L)).thenReturn(Optional.empty());

        // When & Then
        mockMvc.perform(get("/admin/products/1").with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "testuser", roles = "ADMIN")
    void newProduct_ShouldReturnNewProductForm() throws Exception {
        // Given
        when(categoryService.findAllCategories()).thenReturn(List.of(createTestCategory()));

        // When & Then
        mockMvc.perform(get("/admin/products/new").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/products/new"))
                .andExpect(model().attributeExists("product", "categories", "parsedImageUrls"));
    }

    @Test
    @WithMockUser(username = "testuser", roles = "ADMIN")
    void create_WithImage_ShouldSaveProductWithImage() throws Exception {
        // Given
        stubAdminUser();
        Product product = createTestProductWithDetails();
        ProductImage productImage = createTestProductImage();

        when(productService.saveEntity(any(Product.class))).thenReturn(product);
        when(imageStorageService.isImage(any())).thenReturn(true);
        when(imageStorageService.saveImage(any(), anyString())).thenReturn(productImage);

        MockMultipartFile imageFile = new MockMultipartFile(
                "images", "test.jpg", "image/jpeg", "test image content".getBytes()
        );

        // When & Then
        mockMvc.perform(multipart("/admin/products")
                        .file(imageFile)
                        .param("name", "Test Product")
                        .param("title", "test-product")
                        .param("price", "999.99")
                        .param("currency", "RUB")
                        .param("active", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/products"));

        verify(productService, times(2)).saveEntity(any(Product.class));
        verify(imageStorageService, times(1)).saveImage(any(), anyString());
    }

    @Test
    @WithMockUser(username = "testuser", roles = "ADMIN")
    void create_WithMultipleImages_ShouldSaveAllImages() throws Exception {
        // Given
        stubAdminUser();
        Product product = createTestProductWithDetails();
        ProductImage image1 = ProductImage.builder().id(1L).url("url1").alt("alt1").build();
        ProductImage image2 = ProductImage.builder().id(2L).url("url2").alt("alt2").build();

        when(productService.saveEntity(any(Product.class))).thenReturn(product);
        when(imageStorageService.isImage(any())).thenReturn(true);
        when(imageStorageService.saveImage(any(), anyString()))
                .thenReturn(image1)
                .thenReturn(image2);

        MockMultipartFile imageFile1 = new MockMultipartFile(
                "images", "test1.jpg", "image/jpeg", "content1".getBytes()
        );
        MockMultipartFile imageFile2 = new MockMultipartFile(
                "images", "test2.jpg", "image/jpeg", "content2".getBytes()
        );

        // When & Then
        mockMvc.perform(multipart("/admin/products")
                        .file(imageFile1)
                        .file(imageFile2)
                        .param("name", "Test Product")
                        .param("title", "test-product")
                        .param("price", "999.99")
                        .param("currency", "RUB")
                        .param("active", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/products"));

        verify(imageStorageService, times(2)).saveImage(any(), anyString());
        verify(imageStorageService, times(2)).isImage(any());
    }

    @Test
    @WithMockUser(username = "testuser", roles = "ADMIN")
    void create_WithInvalidImage_ShouldSkipInvalidFile() throws Exception {
        // Given
        stubAdminUser();
        Product product = createTestProductWithDetails();

        when(productService.saveEntity(any(Product.class))).thenReturn(product);
        when(imageStorageService.isImage(any())).thenReturn(false);

        MockMultipartFile invalidFile = new MockMultipartFile(
                "images", "test.txt", "text/plain", "text content".getBytes()
        );

        // When & Then
        mockMvc.perform(multipart("/admin/products")
                        .file(invalidFile)
                        .param("name", "Test Product")
                        .param("title", "test-product")
                        .param("price", "999.99")
                        .param("currency", "RUB")
                        .param("active", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/products"));

        verify(imageStorageService, never()).saveImage(any(), anyString());
        verify(imageStorageService, times(1)).isImage(any());
        verify(productService, times(2)).saveEntity(any(Product.class));
    }

    @Test
    @WithMockUser(username = "testuser", roles = "ADMIN")
    void create_WithEmptyImage_ShouldSkipEmptyFile() throws Exception {
        // Given
        stubAdminUser();
        Product product = createTestProductWithDetails();

        when(productService.saveEntity(any(Product.class))).thenReturn(product);

        MockMultipartFile emptyFile = new MockMultipartFile(
                "images", "empty.jpg", "image/jpeg", new byte[0]
        );

        // When & Then
        mockMvc.perform(multipart("/admin/products")
                        .file(emptyFile)
                        .param("name", "Test Product")
                        .param("title", "test-product")
                        .param("price", "999.99")
                        .param("currency", "RUB")
                        .param("active", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/products"));

        verify(imageStorageService, never()).saveImage(any(), anyString());
        verify(imageStorageService, never()).isImage(any());
        verify(productService, times(2)).saveEntity(any(Product.class));
    }

    @Test
    @WithMockUser(username = "testuser", roles = "ADMIN")
    void create_ShouldSaveProductAndRedirect() throws Exception {
        // Given
        stubAdminUser();
        Product product = createTestProductWithDetails();
        when(productService.saveEntity(any(Product.class))).thenReturn(product);
        when(categoryService.findByIds(any())).thenReturn(List.of());

        // When & Then
        mockMvc.perform(post("/admin/products").with(csrf())
                        .param("name", "New Product")
                        .param("title", "new-product")
                        .param("sku", "NEW001")
                        .param("price", "1000.00")
                        .param("currency", "RUB")
                        .param("shortDescription", "Short desc")
                        .param("description", "Full desc")
                        .param("active", "true")
                        .param("categoryIds", "1", "2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/products"));

        verify(productService, times(2)).saveEntity(any(Product.class));
    }

    @Test
    @WithMockUser(username = "testuser", roles = "ADMIN")
    void edit_WhenProductExists_ShouldReturnEditForm() throws Exception {
        // Given
        Product product = createTestProductWithDetails();
        when(productService.findByIdWithAllRelations(1L)).thenReturn(Optional.of(product));
        when(categoryService.findAllCategories()).thenReturn(List.of(createTestCategory()));

        // When & Then
        mockMvc.perform(get("/admin/products/1/edit").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/products/edit"))
                .andExpect(model().attributeExists("product", "categories"));
    }

    @Test
    @WithMockUser(username = "testuser", roles = "ADMIN")
    void editProductForm_ShouldLoadAttributesAndValues() throws Exception {
        // Arrange
        Long productId = 1L;

        Category category = Category.builder()
                .id(1L)
                .name("Электроинструменты")
                .attributes(new HashSet<>())
                .build();

        Attribute attribute = Attribute.builder()
                .id(1L)
                .name("Мощность")
                .category(category)
                .build();

        ProductAttributeValue attributeValue = ProductAttributeValue.builder()
                .attribute(attribute)
                .value("850")
                .build();

        Product product = Product.builder()
                .id(productId)
                .name("Дрель PRO")
                .categories(new HashSet<>())
                .attributeValues(Set.of(attributeValue))
                .build();

        when(productService.findByIdWithAllRelations(productId)).thenReturn(Optional.of(product));
        when(categoryService.findAllCategories()).thenReturn(List.of(category));

        // Act & Assert
        mockMvc.perform(get("/admin/products/{id}/edit", productId).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/products/edit"))
                .andExpect(model().attributeExists("product", "currentValues"))
                .andExpect(model().attribute("currentValues", hasKey(1L)))
                .andExpect(model().attribute("currentValues", hasEntry(1L, "850")));
    }

    @Test
    @WithMockUser(username = "testuser", roles = "ADMIN")
    void updateInfo_ShouldUpdateProductAndRedirect() throws Exception {
        // Given
        stubAdminUser();
        Product existingProduct = createTestProductWithDetails();
        when(productService.findByIdWithAllRelations(1L)).thenReturn(Optional.of(existingProduct));
        when(categoryService.findByIds(any())).thenReturn(List.of());

        // When & Then
        mockMvc.perform(post("/admin/products/1/update-info").with(csrf())
                        .param("name", "Updated Product")
                        .param("title", "updated-product")
                        .param("sku", "UPD001")
                        .param("price", "1500.00")
                        .param("currency", "RUB")
                        .param("shortDescription", "Updated short desc")
                        .param("description", "Updated full desc")
                        .param("active", "true")
                        .param("categoryIds", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/products/1#main-info"))
                .andExpect(flash().attributeExists("message"));

        verify(productService, times(1)).saveEntity(any(Product.class));
    }

    @Test
    @WithMockUser(username = "testuser", roles = "ADMIN")
    void updateImages_WithNewImage_ShouldAddImageToProduct() throws Exception {
        // Given
        stubAdminUser();
        Product existingProduct = createTestProductWithDetails();
        ProductImage newImage = createTestProductImage();

        when(productService.findByIdWithAllRelations(1L)).thenReturn(Optional.of(existingProduct));
        when(imageStorageService.isImage(any())).thenReturn(true);
        when(imageStorageService.saveImage(any(), anyString())).thenReturn(newImage);

        MockMultipartFile newImageFile = new MockMultipartFile(
                "newImages", "new-image.jpg", "image/jpeg", "new image content".getBytes()
        );

        // When & Then
        mockMvc.perform(multipart("/admin/products/1/update-images")
                        .file(newImageFile)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/products/1#images"))
                .andExpect(flash().attributeExists("message"));

        verify(imageStorageService, times(1)).saveImage(any(), anyString());
        verify(productService, times(1)).saveEntity(existingProduct);
    }

    @Test
    @WithMockUser(username = "testuser", roles = "ADMIN")
    void updateImages_WithDeleteImageIds_ShouldRemoveImages() throws Exception {
        // Given
        stubAdminUser();
        Product existingProduct = createTestProductWithDetails();
        ProductImage imageToDelete = createTestProductImage();
        existingProduct.getImages().add(imageToDelete);

        when(productService.findByIdWithAllRelations(1L)).thenReturn(Optional.of(existingProduct));
        doNothing().when(imageStorageService).deleteImage(anyString());

        // When & Then
        mockMvc.perform(multipart("/admin/products/1/update-images")
                        .with(csrf())
                        .param("deleteImageIds", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/products/1#images"));

        verify(imageStorageService, times(1))
                .deleteImage("http://localhost:8080/images/test_product_123.jpg");
        verify(productService, times(1)).saveEntity(existingProduct);
    }

    @Test
    @WithMockUser(username = "testuser", roles = "ADMIN")
    void updateImages_WithNewImageAndDeleteExisting_ShouldHandleBoth() throws Exception {
        // Given
        stubAdminUser();
        Product existingProduct = createTestProductWithDetails();
        ProductImage existingImage = createTestProductImage();
        ProductImage newImage = ProductImage.builder().id(2L).url("new-url").alt("new").build();

        existingProduct.getImages().add(existingImage);

        when(productService.findByIdWithAllRelations(1L)).thenReturn(Optional.of(existingProduct));
        when(imageStorageService.isImage(any())).thenReturn(true);
        when(imageStorageService.saveImage(any(), anyString())).thenReturn(newImage);
        doNothing().when(imageStorageService).deleteImage(anyString());

        MockMultipartFile newImageFile = new MockMultipartFile(
                "newImages", "new.jpg", "image/jpeg", "content".getBytes()
        );

        // When & Then
        mockMvc.perform(multipart("/admin/products/1/update-images")
                        .file(newImageFile)
                        .with(csrf())
                        .param("deleteImageIds", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/products/1#images"));

        verify(imageStorageService, times(1))
                .deleteImage("http://localhost:8080/images/test_product_123.jpg");
        verify(imageStorageService, times(1)).saveImage(any(), anyString());
        verify(productService, times(1)).saveEntity(existingProduct);
    }

    @Test
    @WithMockUser(username = "testuser", roles = "ADMIN")
    void delete_ShouldDeleteProductAndRedirect() throws Exception {
        // Given
        doNothing().when(productService).deleteEntity(1L);

        // When & Then
        mockMvc.perform(post("/admin/products/1/delete").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/products"));

        verify(productService, times(1)).deleteEntity(1L);
    }

    @Test
    @WithMockUser(username = "testuser", roles = "ADMIN")
    void specificationsForm_ShouldReturnSpecificationsView() throws Exception {
        // Arrange
        Long productId = 1L;

        Category category = Category.builder()
                .id(1L)
                .name("Электроинструменты")
                .attributes(new HashSet<>())
                .build();

        Product product = Product.builder()
                .id(productId)
                .name("Дрель PRO")
                .categories(Set.of(category))
                .attributeValues(new LinkedHashSet<>())
                .build();

        when(productService.findWithDetailsById(productId)).thenReturn(Optional.of(product));

        // Act & Assert
        mockMvc.perform(get("/admin/products/{id}/specifications", productId).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/products/specifications"))
                .andExpect(model().attributeExists("product", "attributes", "currentValues"));
    }

    @Test
    @WithMockUser(username = "testuser", roles = "ADMIN")
    void saveSpecifications_WithValidData_ShouldSaveAndRedirect() throws Exception {
        // Arrange
        stubAdminUser();
        Long productId = 1L;
        Product product = Product.builder().id(productId).name("Дрель PRO").build();

        when(productService.findWithDetailsById(productId)).thenReturn(Optional.of(product));

        // Act & Assert
        mockMvc.perform(post("/admin/products/{id}/specifications", productId)
                        .with(csrf())
                        .param("attr_1", "850")
                        .param("attr_2", "Черный")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/products/" + productId));

        verify(attributeService).saveProductAttributes(eq(product), any(Map.class));
    }
}
