package ru.fisher.ToolsMarket.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.fisher.ToolsMarket.models.Category;
import ru.fisher.ToolsMarket.models.Product;
import ru.fisher.ToolsMarket.models.ProductImage;
import ru.fisher.ToolsMarket.service.CategoryService;
import ru.fisher.ToolsMarket.service.ImageStorageService;
import ru.fisher.ToolsMarket.service.ProductService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ProductAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ProductService productService;
    @MockitoBean
    private CategoryService categoryService;
    @MockitoBean
    private ImageStorageService imageStorageService;


    private Product createTestProduct() {
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
                .categories(new HashSet<>()) // Инициализируем коллекцию
                .images(new ArrayList<>())   // Инициализируем коллекцию изображений
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

    @Test
    void create_WithImage_ShouldSaveProductWithImage() throws Exception {
        // Given
        Product product = createTestProduct();
        ProductImage productImage = createTestProductImage();

        when(productService.saveEntity(any(Product.class))).thenReturn(product);
        when(imageStorageService.isImage(any())).thenReturn(true);
        when(imageStorageService.saveImage(any(), anyString())).thenReturn(productImage);
        when(categoryService.findAllEntities()).thenReturn(List.of());

        MockMultipartFile imageFile = new MockMultipartFile(
                "images",
                "test.jpg",
                "image/jpeg",
                "test image content".getBytes()
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
    void create_WithMultipleImages_ShouldSaveAllImages() throws Exception {
        // Given
        Product product = createTestProduct();
        ProductImage image1 = ProductImage.builder().id(1L).url("url1").alt("alt1").build();
        ProductImage image2 = ProductImage.builder().id(2L).url("url2").alt("alt2").build();

        when(productService.saveEntity(any(Product.class))).thenReturn(product);
        when(imageStorageService.isImage(any())).thenReturn(true);
        when(imageStorageService.saveImage(any(), anyString()))
                .thenReturn(image1)  // Первый вызов вернет image1
                .thenReturn(image2); // Второй вызов вернет image2
        when(categoryService.findAllEntities()).thenReturn(List.of());

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

        // Проверяем, что saveImage вызвался 2 раза
        verify(imageStorageService, times(2)).saveImage(any(), anyString());
        verify(imageStorageService, times(2)).isImage(any());
    }

    @Test
    void create_WithInvalidImage_ShouldSkipInvalidFile() throws Exception {
        // Given
        Product product = createTestProduct();

        when(productService.saveEntity(any(Product.class))).thenReturn(product);
        when(imageStorageService.isImage(any())).thenReturn(false); // Файлы не являются изображениями
        when(categoryService.findAllEntities()).thenReturn(List.of());

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

        // saveImage не должен вызываться для невалидных файлов
        verify(imageStorageService, never()).saveImage(any(), anyString());
        // Но isImage должен вызваться
        verify(imageStorageService, times(1)).isImage(any());
        // Продукт все равно должен сохраниться
        verify(productService, atLeastOnce()).saveEntity(any(Product.class));
    }

    @Test
    void create_WithEmptyImage_ShouldSkipEmptyFile() throws Exception {
        // Given
        Product product = createTestProduct();

        when(productService.saveEntity(any(Product.class))).thenReturn(product);
        // isImage не будет вызываться для пустых файлов
        when(categoryService.findAllEntities()).thenReturn(List.of());

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
    }

    @Test
    void update_WithNewImage_ShouldAddImageToProduct() throws Exception {
        // Given
        Product existingProduct = createTestProduct();
        ProductImage newImage = createTestProductImage();

        when(productService.findEntityById(1L)).thenReturn(Optional.of(existingProduct));
        when(imageStorageService.isImage(any())).thenReturn(true);
        when(imageStorageService.saveImage(any(), anyString())).thenReturn(newImage);

        MockMultipartFile newImageFile = new MockMultipartFile(
                "newImages",
                "new-image.jpg",
                "image/jpeg",
                "new image content".getBytes()
        );

        // When & Then
        mockMvc.perform(multipart("/admin/products/1")
                        .file(newImageFile)
                        .param("name", "Updated Product")
                        .param("title", "updated-product")
                        .param("price", "1099.99")
                        .param("currency", "RUB")
                        .param("active", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/products"));

        verify(imageStorageService, times(1)).saveImage(any(), anyString());
        verify(productService, times(1)).saveEntity(existingProduct);
    }

    @Test
    void update_WithDeleteImageIds_ShouldRemoveImages() throws Exception {
        // Given
        Product existingProduct = createTestProduct();
        ProductImage imageToDelete = createTestProductImage();
        existingProduct.getImages().add(imageToDelete);

        when(productService.findEntityById(1L)).thenReturn(Optional.of(existingProduct));
        doNothing().when(imageStorageService).deleteImage(anyString());

        // When & Then
        mockMvc.perform(multipart("/admin/products/1")
                        .param("name", "Updated Product")
                        .param("title", "updated-product")
                        .param("price", "1099.99")
                        .param("currency", "RUB")
                        .param("active", "true")
                        .param("deleteImageIds", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/products"));

        verify(imageStorageService, times(1)).deleteImage("http://localhost:8080/images/test_product_123.jpg");
        verify(productService, times(1)).saveEntity(existingProduct);
    }

    @Test
    void update_WithNewImageAndDeleteExisting_ShouldHandleBoth() throws Exception {
        // Given
        Product existingProduct = createTestProduct();
        ProductImage existingImage = createTestProductImage();
        ProductImage newImage = ProductImage.builder().id(2L).url("new-url").alt("new").build();

        existingProduct.getImages().add(existingImage);

        when(productService.findEntityById(1L)).thenReturn(Optional.of(existingProduct));
        when(imageStorageService.isImage(any())).thenReturn(true);
        when(imageStorageService.saveImage(any(), anyString())).thenReturn(newImage);
        doNothing().when(imageStorageService).deleteImage(anyString());

        MockMultipartFile newImageFile = new MockMultipartFile(
                "newImages", "new.jpg", "image/jpeg", "content".getBytes()
        );

        // When & Then
        mockMvc.perform(multipart("/admin/products/1")
                        .file(newImageFile)
                        .param("name", "Updated Product")
                        .param("title", "updated-product")
                        .param("price", "1099.99")
                        .param("currency", "RUB")
                        .param("active", "true")
                        .param("deleteImageIds", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/products"));

        verify(imageStorageService, times(1)).deleteImage("http://localhost:8080/images/test_product_123.jpg");
        verify(imageStorageService, times(1)).saveImage(any(), anyString());
        verify(productService, times(1)).saveEntity(existingProduct);
    }

    private Category createTestCategory() {
        return Category.builder()
                .id(1L)
                .name("Test Category")
                .title("test-category")
                .build();
    }

    @Test
    void index_ShouldReturnProductsList() throws Exception {
        // Given
        Product product = createTestProduct();
        when(productService.findAllEntities()).thenReturn(List.of(product));

        // When & Then
        mockMvc.perform(get("/admin/products"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/products/index"))
                .andExpect(model().attributeExists("products"))
                .andExpect(model().attribute("products", List.of(product)));
    }

    @Test
    void show_WhenProductExists_ShouldReturnProductView() throws Exception {
        // Given
        Product product = createTestProduct();
        when(productService.findEntityById(1L)).thenReturn(Optional.of(product));

        // When & Then
        mockMvc.perform(get("/admin/products/1"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/products/show"))
                .andExpect(model().attributeExists("product"))
                .andExpect(model().attribute("product", product));
    }

    @Test
    void show_WhenProductNotExists_ShouldReturnNotFound() throws Exception {
        // Given
        when(productService.findEntityById(1L)).thenReturn(Optional.empty());

        // When & Then
        mockMvc.perform(get("/admin/products/1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void newProduct_ShouldReturnNewProductForm() throws Exception {
        // Given
        when(categoryService.findAllEntities()).thenReturn(List.of(createTestCategory()));

        // When & Then
        mockMvc.perform(get("/admin/products/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/products/new"))
                .andExpect(model().attributeExists("product"))
                .andExpect(model().attributeExists("categories"));
    }

    @Test
    void create_ShouldSaveProductAndRedirect() throws Exception {
        // Given
        Product product = createTestProduct();
        when(productService.saveEntity(any(Product.class))).thenReturn(product);

        // When & Then
        mockMvc.perform(post("/admin/products")
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

        verify(productService, times(1)).saveEntity(any(Product.class));
    }

    @Test
    void edit_WhenProductExists_ShouldReturnEditForm() throws Exception {
        // Given
        Product product = createTestProduct();
        when(productService.findEntityById(1L)).thenReturn(Optional.of(product));
        when(categoryService.findAllEntities()).thenReturn(List.of(createTestCategory()));

        // When & Then
        mockMvc.perform(get("/admin/products/1/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/products/edit"))
                .andExpect(model().attributeExists("product"))
                .andExpect(model().attributeExists("categories"));
    }

    @Test
    void update_ShouldUpdateProductAndRedirect() throws Exception {
        // Given
        Product existingProduct = createTestProduct();
        when(productService.findEntityById(1L)).thenReturn(Optional.of(existingProduct));
        when(productService.saveEntity(any(Product.class))).thenReturn(existingProduct);

        // When & Then
        mockMvc.perform(post("/admin/products/1")
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
                .andExpect(redirectedUrl("/admin/products"));

        verify(productService, times(1)).saveEntity(any(Product.class));
    }

    @Test
    void delete_ShouldDeleteProductAndRedirect() throws Exception {
        // Given
        doNothing().when(productService).deleteEntity(1L);

        // When & Then
        mockMvc.perform(post("/admin/products/1/delete"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/products"));

        verify(productService, times(1)).deleteEntity(1L);
    }

}