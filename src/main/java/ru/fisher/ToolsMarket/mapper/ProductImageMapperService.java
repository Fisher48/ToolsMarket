package ru.fisher.ToolsMarket.mapper;

import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import ru.fisher.ToolsMarket.dto.ProductImageDto;
import ru.fisher.ToolsMarket.models.ProductImage;


@Service
@RequiredArgsConstructor
public class ProductImageMapperService {

    private final ModelMapper modelMapper;

    public ProductImageDto toDto(ProductImage productImage) {
        if (productImage == null) return null;

        ProductImageDto dto = modelMapper.map(productImage, ProductImageDto.class);

        // Устанавливаем productId
        if (productImage.getProduct() != null) {
            dto.setId(productImage.getProduct().getId());
        }

        return dto;
    }
}
