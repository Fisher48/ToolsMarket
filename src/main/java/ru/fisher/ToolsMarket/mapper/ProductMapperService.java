package ru.fisher.ToolsMarket.mapper;

import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import ru.fisher.ToolsMarket.dto.ProductCreateDto;
import ru.fisher.ToolsMarket.dto.ProductDto;
import ru.fisher.ToolsMarket.dto.ProductListDto;
import ru.fisher.ToolsMarket.dto.ProductUpdateDto;
import ru.fisher.ToolsMarket.models.Product;

import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductMapperService {

    private final ModelMapper modelMapper;
    private final CategoryMapperService categoryMapperService;
    private final ProductImageMapperService productImageMapperService;

    public ProductDto toDto(Product product) {
        if (product == null) return null;

        ProductDto dto = modelMapper.map(product, ProductDto.class);

        // Маппим только простые категории (без родителей/детей)
        if (product.getCategories() != null) {
            dto.setCategories(product.getCategories().stream()
                    .map(categoryMapperService::toSimpleDto) // Используем простой DTO
                    .collect(Collectors.toSet()));
        }

        // Маппим изображения
        if (product.getImages() != null) {
            dto.setImages(product.getImages().stream()
                    .map(productImageMapperService::toDto)
                    .collect(Collectors.toList()));
        }

        return dto;
    }

    public ProductListDto toListDto(Product product) {
        if (product == null) return null;

        ProductListDto dto = modelMapper.map(product, ProductListDto.class);

        // Устанавливаем главное изображение
        String mainImageUrl = product.getImages() != null && !product.getImages().isEmpty() ?
                product.getImages().iterator().next().getUrl() : "/images/placeholder.jpg";
        dto.setMainImageUrl(mainImageUrl);

        return dto;
    }

    public Product toEntity(ProductCreateDto dto) {
        return modelMapper.map(dto, Product.class);
    }

    public void updateEntityFromDto(ProductUpdateDto dto, Product product) {
        modelMapper.map(dto, product);
    }
}
