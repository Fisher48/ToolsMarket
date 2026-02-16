package ru.fisher.ToolsMarket.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ru.fisher.ToolsMarket.dto.ImageOrderDto;
import ru.fisher.ToolsMarket.models.ProductImage;
import ru.fisher.ToolsMarket.repository.ProductImageRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ImageStorageService {

    @Value("${app.upload.path:./uploads/images}")
    private String uploadPath;

    @Value("${app.base.url:http://localhost:8080}")
    private String baseUrl;

    private final ProductImageRepository productImageRepository;

    public ProductImage saveImage(MultipartFile file, String productTitle) {
        log.info("Attempting to save image: {}, size: {}, type: {}",
                file.getOriginalFilename(), file.getSize(), file.getContentType());

        if (file.isEmpty()) {
            throw new IllegalArgumentException("Файл пустой");
        }

        if (!isImage(file)) {
            throw new IllegalArgumentException("Файл не является изображением");
        }

        try {
            // Создаем директорию если не существует
            Path uploadDir = Paths.get(uploadPath, "products");
            Files.createDirectories(uploadDir);

            // Генерируем уникальное имя файла
            String originalFileName = file.getOriginalFilename();
            String fileExtension = getFileExtension(originalFileName);
            String fileName = generateFileName(productTitle, fileExtension);

            // Сохраняем файл
            Path filePath = uploadDir.resolve(fileName);
            Files.write(filePath, file.getBytes());

            log.info("Image saved: {}", filePath);

            // Создаем и возвращаем сущность ProductImage
            return ProductImage.builder()
                    .url(baseUrl + "/images/products/" + fileName) // Полный URL
                    .alt(productTitle) // Базовое описание
                    .sortOrder(0)
                    .build();

        } catch (IOException e) {
            throw new RuntimeException("Failed to save image: " + e.getMessage(), e);
        }
    }

    @Transactional
    public void updateImageOrder(List<ImageOrderDto> orderData) {
        log.info("Обновление порядка сортировки изображений: {}", orderData);

        for (ImageOrderDto dto : orderData) {
            ProductImage image = productImageRepository.findById(dto.getId())
                    .orElseThrow(() -> new RuntimeException("Изображение не найдено: " + dto.getId()));

            image.setSortOrder(dto.getSortOrder());
            productImageRepository.save(image);
        }
    }

    public List<ProductImage> saveImages(List<MultipartFile> files, String productTitle) {
        List<ProductImage> productImages = new ArrayList<>();

        for (MultipartFile file : files) {
            if (!file.isEmpty() && isImage(file)) {
                try {
                    ProductImage productImage = saveImage(file, productTitle);
                    productImages.add(productImage);
                } catch (Exception e) {
                    log.warn("Не удалось сохранить изображение: {}", file.getOriginalFilename(), e);
                }
            }
        }
        return productImages;
    }

    public void deleteImage(String imageUrl) {
        try {
            // Извлекаем имя файла из URL
            String fileName = extractFileNameFromUrl(imageUrl);
            Path filePath = Paths.get(uploadPath,"products", fileName);

            if (Files.exists(filePath)) {
                Files.delete(filePath);
                log.info("Image deleted: {}", filePath);
            } else {
                log.warn("File not found for deletion: {}", filePath);
            }
        } catch (IOException e) {
            log.error("Failed to delete image: {}", e.getMessage());
        }
    }

    public boolean isImage(MultipartFile file) {
        String contentType = file.getContentType();
        return contentType != null && contentType.startsWith("image/");
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return ".jpg";
        }
        return fileName.substring(fileName.lastIndexOf("."));
    }

    private String generateFileName(String productTitle, String extension) {
        String safeTitle = productTitle.replaceAll("[^a-zA-Z0-9-]", "_");
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        return safeTitle + "_" + uniqueId + "_" + System.currentTimeMillis() + extension;
    }

    private String extractFileNameFromUrl(String imageUrl) {
        // Извлекаем имя файла из URL: http://localhost:8080/images/filename.jpg -> filename.jpg
        if (imageUrl.contains("/")) {
            return imageUrl.substring(imageUrl.lastIndexOf("/") + 1);
        }
        return imageUrl;
    }

    /**
     * Сохраняет изображение категории
     */
    public String saveCategoryImage(MultipartFile file, String categoryTitle) {
        log.info("Saving category image: {}, category: {}, size: {}",
                file.getOriginalFilename(), categoryTitle, file.getSize());

        if (file.isEmpty()) {
            throw new IllegalArgumentException("Файл пустой");
        }

        if (!isImage(file)) {
            throw new IllegalArgumentException("Файл не является изображением");
        }

        // Проверяем размер для категорий (максимум 2MB)
        if (file.getSize() > 2 * 1024 * 1024) {
            throw new IllegalArgumentException("Размер изображения категории не должен превышать 2MB");
        }

        try {
            // Создаем поддиректорию для категорий
            Path categoryDir = Paths.get(uploadPath, "categories");
            Files.createDirectories(categoryDir);

            // Генерируем уникальное имя файла
            String originalFileName = file.getOriginalFilename();
            String fileExtension = getFileExtension(originalFileName);
            String fileName = generateCategoryFileName(categoryTitle, fileExtension);

            // Сохраняем файл
            Path filePath = categoryDir.resolve(fileName);
            Files.write(filePath, file.getBytes());

            // Формируем URL для доступа к изображению
            String imageUrl = baseUrl + "/images/categories/" + fileName;
            log.info("Category image saved: {}", imageUrl);

            return imageUrl;

        } catch (IOException e) {
            log.error("Failed to save category image: {}", e.getMessage(), e);
            throw new RuntimeException("Не удалось сохранить изображение категории: " + e.getMessage(), e);
        }
    }

    /**
     * Генерирует миниатюру для категории (пока возвращает тот же URL)
     */
    public String generateThumbnail(String originalImageUrl) {
        log.info("Generating thumbnail for: {}", originalImageUrl);
        // Пока просто возвращаем тот же URL
        // В будущем можно реализовать реальную генерацию миниатюр
        return originalImageUrl;
    }

    /**
     * Удаляет изображение категории
     */
    public void deleteCategoryImage(String imageUrl) {
        try {
            // Извлекаем имя файла из URL
            String fileName = extractFileNameFromUrl(imageUrl);

            // Учитываем что файл в поддиректории categories
            Path filePath = Paths.get(uploadPath, "categories", fileName);

            if (Files.exists(filePath)) {
                Files.delete(filePath);
                log.info("Category image deleted: {}", filePath);
            } else {
                log.warn("Category image file not found: {}", filePath);
            }
        } catch (IOException e) {
            log.error("Failed to delete category image: {}", e.getMessage(), e);
        }
    }

    /**
     * Генерирует имя файла для категории
     */
    private String generateCategoryFileName(String categoryTitle, String extension) {
        String safeTitle = categoryTitle.replaceAll("[^a-zA-Z0-9-]", "_");
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        return "category_" + safeTitle + "_" + uniqueId + "_" + System.currentTimeMillis() + extension;
    }

    @PostConstruct
    public void init() {
        try {
            // Создаем основную директорию
            Files.createDirectories(Paths.get(uploadPath));

            // Создаем поддиректорию для категорий
            Path categoryDir = Paths.get(uploadPath, "categories");
            Files.createDirectories(categoryDir);

            log.info("Upload directories created:");
            log.info(" - Main: {}", uploadPath);
            log.info(" - Categories: {}", categoryDir);

        } catch (IOException e) {
            log.warn("Could not create upload directories: {}", e.getMessage());
        }
    }
}
