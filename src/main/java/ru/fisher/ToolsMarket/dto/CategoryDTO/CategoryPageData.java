package ru.fisher.ToolsMarket.dto.CategoryDTO;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.domain.Page;
import ru.fisher.ToolsMarket.dto.ProductDTO.ProductCardDto;

import java.util.Map;

@Data
@Builder
public class CategoryPageData {
    private CategoryDto category;
    private Page<ProductCardDto> products;
    private Map<Long, Integer> cartProductQuantities;
    private long totalElements;
}
