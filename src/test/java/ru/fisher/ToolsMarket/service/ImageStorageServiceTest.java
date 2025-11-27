package ru.fisher.ToolsMarket.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.multipart.MultipartFile;
import ru.fisher.ToolsMarket.PostgresTestConfig;
import ru.fisher.ToolsMarket.models.ProductImage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;


@SpringBootTest
@TestPropertySource(properties = {
        "app.upload.path=./test-uploads",
        "app.base.url=http://testlocalhost:8080"
})
@ContextConfiguration(initializers = PostgresTestConfig.class)
class ImageStorageServiceTest {

    @Autowired
    private ImageStorageService imageStorageService;

    @TempDir
    static Path tempDir;

    @Test
    void saveImage_WithValidImage_ShouldSaveAndReturnProductImage() throws IOException {
        // Given
        MockMultipartFile file = new MockMultipartFile(
                "test.jpg",
                "test.jpg",
                "image/jpeg",
                "test image content".getBytes()
        );
        String productTitle = "TestProduct";

        // When
        ProductImage result = imageStorageService.saveImage(file, productTitle);

        // Then
        assertNotNull(result);
        assertNotNull(result.getUrl());
        assertTrue(result.getUrl().startsWith("http://testlocalhost:8080/images/"));
        assertTrue(result.getUrl().toLowerCase().contains("testproduct"));
        assertEquals("TestProduct", result.getAlt());
        assertEquals(0, result.getSortOrder());
    }

    @Test
    void fileNameGeneration_ShouldCreateUniqueNames() throws IOException {
        // Given
        MockMultipartFile file1 = new MockMultipartFile("test1.jpg", "test1.jpg", "image/jpeg", "content1".getBytes());
        MockMultipartFile file2 = new MockMultipartFile("test2.jpg", "test2.jpg", "image/jpeg", "content2".getBytes());
        String productTitle = "SameProduct";

        // When
        ProductImage result1 = imageStorageService.saveImage(file1, productTitle);
        ProductImage result2 = imageStorageService.saveImage(file2, productTitle);

        // Then
        assertNotNull(result1.getUrl());
        assertNotNull(result2.getUrl());
        assertNotEquals(result1.getUrl(), result2.getUrl(), "URLs should be different for different files");
        assertTrue(result1.getUrl().contains("sameproduct") || result1.getUrl().contains("SameProduct"));
        assertTrue(result2.getUrl().contains("sameproduct") || result2.getUrl().contains("SameProduct"));
    }
}