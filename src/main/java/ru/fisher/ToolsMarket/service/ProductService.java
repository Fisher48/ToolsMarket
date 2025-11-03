package ru.fisher.ToolsMarket.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.fisher.ToolsMarket.dto.*;
import ru.fisher.ToolsMarket.mapper.ProductImageMapperService;
import ru.fisher.ToolsMarket.mapper.ProductMapperService;
import ru.fisher.ToolsMarket.models.Category;
import ru.fisher.ToolsMarket.models.Product;
import ru.fisher.ToolsMarket.models.ProductImage;
import ru.fisher.ToolsMarket.repository.CategoryRepository;
import ru.fisher.ToolsMarket.repository.ProductRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapperService productMapperService;
    private final ProductImageMapperService productImageMapperService;

    @Transactional(readOnly = true)
    public List<Product> findAllEntities() {
        return productRepository.findAll();
    }

    @Transactional
    public Product saveEntity(Product product) {
        return productRepository.save(product);
    }

    @Transactional
    public void deleteEntity(Long id) {
        productRepository.deleteById(id);
    }

    public Optional<ProductDto> findByTitle(String title) {
        return productRepository.findByTitle(title)
                .map(productMapperService::toDto);
    }

    public List<ProductDto> findAll() {
        return productRepository.findAll()
                .stream()
                .map(productMapperService::toDto)
                .toList();
    }

    public Page<ProductListDto> findAll(Pageable pageable) {
        return productRepository.findAll(pageable)
                .map(productMapperService::toListDto);
    }

    @Transactional
    public void addImage(Long productId, ProductImageDto imageDto) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        ProductImage image = productImageMapperService.toEntity(imageDto, product);
        product.getImages().add(image);
        productRepository.save(product);
    }

    public Optional<Product> findEntityById(Long id) {
        return productRepository.findById(id);
    }

    public Optional<ProductDto> findById(Long id) {
        return productRepository.findById(id)
                .map(productMapperService::toDto);
    }

    public Page<ProductListDto> findByCategory(Long categoryId, Pageable pageable) {
        return productRepository.findActiveByCategory(categoryId, pageable)
                .map(productMapperService::toListDto);
    }

    public Page<ProductListDto> search(String query, Pageable pageable) {
        if (query == null || query.trim().isEmpty()) {
            return Page.empty();
        }
        return productRepository.searchActive(query.trim(), pageable)
                .map(productMapperService::toListDto);
    }

    @Transactional
    public ProductDto save(ProductCreateDto productDto) {
        Product product = productMapperService.toEntity(productDto);

        // Устанавливаем категории вручную
        if (productDto.getCategoryIds() != null) {
            Set<Category> categories = new HashSet<>();
            for (Long categoryId : productDto.getCategoryIds()) {
                categoryRepository.findById(categoryId).ifPresent(categories::add);
            }
            product.setCategories(categories);
        }

        Product saved = productRepository.save(product);
        return productMapperService.toDto(saved);
    }

    @Transactional
    public ProductDto update(Long id, ProductUpdateDto productDto) {
        Product existingProduct = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        // Обновляем поля через маппер
        productMapperService.updateEntityFromDto(productDto, existingProduct);

        // Обновляем категории если переданы
        if (productDto.getCategoryIds() != null) {
            Set<Category> categories = new HashSet<>();
            for (Long categoryId : productDto.getCategoryIds()) {
                categoryRepository.findById(categoryId).ifPresent(categories::add);
            }
            existingProduct.setCategories(categories);
        }

        Product updated = productRepository.save(existingProduct);
        return productMapperService.toDto(updated);
    }

    @Transactional
    public void delete(Long id) {
        productRepository.deleteById(id);
    }
}
