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
import java.util.function.BiFunction;

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

        String imageUrl = storeFile(file, "products", productTitle, 0, this::generateFileName);
        log.info("Image saved: {}", imageUrl);

        // Создаем и возвращаем сущность ProductImage
        return ProductImage.builder()
                .url(imageUrl) // Полный URL
                .alt(productTitle) // Базовое описание
                .sortOrder(0)
                .build();
    }

    /**
     * Полиморфное сохранение файла: подпапка, лимит размера и политика имени
     * передаются параметрами, поэтому один и тот же код обслуживает и товары,
     * и категории. Политика имени задаётся функцией, а не отдельной копией метода;
     * добавить новый вид изображения — значит передать новые данные, а не новый код.
     *
     * @param maxBytes 0 — без ограничения размера
     * @return абсолютный URL сохранённого файла
     */
    private String storeFile(MultipartFile file, String folder, String title,
                             long maxBytes, BiFunction<String, String, String> nameFactory) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Файл пустой");
        }

        if (!isImage(file)) {
            throw new IllegalArgumentException("Файл не является изображением");
        }

        if (maxBytes > 0 && file.getSize() > maxBytes) {
            throw new IllegalArgumentException("Размер изображения не должен превышать "
                    + (maxBytes / (1024 * 1024)) + "MB");
        }

        try {
            Path dir = Paths.get(uploadPath, folder);
            Files.createDirectories(dir);

            String fileExtension = getFileExtension(file.getOriginalFilename());
            String fileName = nameFactory.apply(title, fileExtension);
            Files.write(dir.resolve(fileName), file.getBytes());

            return baseUrl + "/images/" + folder + "/" + fileName;
        } catch (IOException e) {
            throw new RuntimeException("Не удалось сохранить изображение: " + e.getMessage(), e);
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

        // Для категорий максимум 2MB
        String imageUrl = storeFile(file, "categories", categoryTitle, 2L * 1024 * 1024,
                this::generateCategoryFileName);
        log.info("Category image saved: {}", imageUrl);

        return imageUrl;
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
