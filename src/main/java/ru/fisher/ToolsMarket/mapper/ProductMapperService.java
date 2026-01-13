package ru.fisher.ToolsMarket.mapper;

import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import ru.fisher.ToolsMarket.dto.*;
import ru.fisher.ToolsMarket.models.Attribute;
import ru.fisher.ToolsMarket.models.Product;
import ru.fisher.ToolsMarket.models.ProductImage;
import ru.fisher.ToolsMarket.models.User;
import ru.fisher.ToolsMarket.service.AttributeService;
import ru.fisher.ToolsMarket.service.DiscountService;
import ru.fisher.ToolsMarket.service.UserService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductMapperService {

    private final ModelMapper modelMapper;
    private final CategoryMapperService categoryMapperService;
    private final ProductImageMapperService productImageMapperService;
    private final AttributeService attributeService;
    private final DiscountService discountService;
    private final UserService userService;

    /**
     * Конвертация Product в ProductDto с учетом скидок текущего пользователя
     */
    public ProductDto toDto(Product product) {
        return toDto(product, getCurrentUser());
    }

    /**
     * Конвертация с указанием пользователя для расчета скидок
     */
    public ProductDto toDto(Product product, User user) {
        ProductDto dto = ProductDto.builder()
                .id(product.getId())
                .name(product.getName())
                .title(product.getTitle())
                .shortDescription(product.getShortDescription())
                .description(product.getDescription())
                .sku(product.getSku())
                .price(product.getPrice())
                .currency(product.getCurrency())
                .active(product.isActive())
                .productType(product.getProductType())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();

        // ДОБАВЛЯЕМ ИЗОБРАЖЕНИЯ
        if (product.getImages() != null) {
            List<ProductImageDto> imageDtos = product.getImages().stream()
                    .map(img -> ProductImageDto.builder()
                            .url(img.getUrl())
                            .alt(img.getAlt())
                            .sortOrder(img.getSortOrder())
                            .build())
                    .toList();
            dto.setImages(imageDtos);
        }

        // ДОБАВЛЯЕМ КАТЕГОРИИ
        if (product.getCategories() != null) {
            Set<CategorySimpleDto> categoryDtos = product.getCategories().stream()
                    .map(cat -> CategorySimpleDto.builder()
                            .id(cat.getId())
                            .name(cat.getName())
                            .title(cat.getTitle())
                            .imageUrl(cat.getImageUrl())
                            .build())
                    .collect(Collectors.toSet());
            dto.setCategories(categoryDtos);
        }

        // ДОБАВЛЯЕМ АТРИБУТЫ
        if (product.getAttributeValues() != null) {
            List<ProductAttributeValueDto> attributeDtos = product.getAttributeValues().stream()
                    .map(attrValue -> ProductAttributeValueDto.builder()
                            .id(attrValue.getId())
                            .attributeName(attrValue.getAttribute().getName())
                            .attributeUnit(attrValue.getAttribute().getUnit())
                            .value(attrValue.getValue())
                            .build())
                    .toList();
            dto.setAttributeValues(attributeDtos);
        }

        // Рассчитываем скидку если есть пользователь
        if (user != null && product.getProductType() != null) {
            BigDecimal discountPercentage = discountService.calculateDiscount(user, product);
            if (discountPercentage.compareTo(BigDecimal.ZERO) > 0) {
                dto.setDiscountPercentage(discountPercentage);
                dto.setDiscountedPrice(discountService.getPriceWithDiscount(user, product));
                dto.setHasDiscount(true);
            }
        }

        return dto;
    }

    /**
     * Для ProductListDto (списка товаров)
     */
    public ProductListDto toListDto(Product product) {
        return toListDto(product, getCurrentUser());
    }

    public ProductListDto toListDto(Product product, User user) {
        ProductListDto dto = ProductListDto.builder()
                .id(product.getId())
                .name(product.getName())
                .title(product.getTitle())
                .shortDescription(product.getShortDescription())
                .sku(product.getSku())
                .price(product.getPrice())
                .currency(product.getCurrency())
                .active(product.isActive())
                .productType(product.getProductType())
                .createdAt(product.getCreatedAt())
                .build();

        // Устанавливаем главное изображение
        if (!product.getImages().isEmpty()) {
            ProductImage mainImage = product.getImages().iterator().next();
            dto.setMainImageUrl(mainImage.getUrl());
            dto.setImages(product.getImages().stream()
                    .map(img -> ProductImageDto.builder()
                            .url(img.getUrl())
                            .alt(img.getAlt())
                            .sortOrder(img.getSortOrder())
                            .build())
                    .toList());
        }

        // Рассчитываем скидку если есть пользователь и productType
        if (user != null && product.getProductType() != null) {
            BigDecimal discountPercentage = discountService.calculateDiscount(user, product);
            if (discountPercentage.compareTo(BigDecimal.ZERO) > 0) {
                dto.setDiscountPercentage(discountPercentage);
                dto.setDiscountedPrice(discountService.getPriceWithDiscount(user, product));
                dto.setHasDiscount(true);
            }
        }

        return dto;
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            String username = authentication.getName();
            return userService.findByUsername(username).orElse(null);
        }
        return null;
    }










//    public ProductDto toDto(Product product) {
//        if (product == null) return null;
//
//        ProductDto dto = modelMapper.map(product, ProductDto.class);
//
//        // Маппим только простые категории (без родителей/детей)
//        if (product.getCategories() != null) {
//            dto.setCategories(product.getCategories().stream()
//                    .map(categoryMapperService::toSimpleDto) // Используем простой DTO
//                    .collect(Collectors.toSet()));
//        }
//
//        // Маппим изображения
//        if (product.getImages() != null) {
//            dto.setImages(product.getImages().stream()
//                    .map(productImageMapperService::toDto)
//                    .collect(Collectors.toList()));
//        }
//
//        // Добавляем характеристики
//        Map<Attribute, String> attributes = attributeService.getProductAttributes(product);
//        dto.setSpecifications(attributes.entrySet().stream()
//                .collect(Collectors.toMap(
//                        entry -> entry.getKey().getName(),
//                        Map.Entry::getValue
//                )));
//
//        // Или полную информацию об атрибутах
//        dto.setAttributeValues(attributeService.getProductAttributeValues(product).stream()
//                .map(ProductAttributeValueDto::fromEntity)
//                .collect(Collectors.toList()));
//
//        return dto;
//    }
//
//    public ProductListDto toListDto(Product product) {
//        if (product == null) return null;
//
//        ProductListDto dto = modelMapper.map(product, ProductListDto.class);
//
//        // Устанавливаем главное изображение
//        String mainImageUrl = product.getImages() != null && !product.getImages().isEmpty() ?
//                product.getImages().iterator().next().getUrl() : "/images/placeholder.jpg";
//        dto.setMainImageUrl(mainImageUrl);
//
//        return dto;
//    }

    public Product toEntity(ProductCreateDto dto) {
        return modelMapper.map(dto, Product.class);
    }

    public void updateEntityFromDto(ProductUpdateDto dto, Product product) {
        modelMapper.map(dto, product);
    }
}
