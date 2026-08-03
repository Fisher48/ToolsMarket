package ru.fisher.ToolsMarket.dto.ProductDTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.fisher.ToolsMarket.util.PriceFormatter;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductCardDto {
    private Long id;
    private String title;
    private String name;
    private String shortDescription;
    private String sku;
    private BigDecimal price;
    private String mainImageUrl;
    private BigDecimal discountPercentage;
    private BigDecimal discountedPrice;
    private boolean hasDiscount;
    private boolean active;
    private int cartQuantity;
    private boolean inCart;

    // Методы для шаблона
    public String getFormattedPrice() {
        return PriceFormatter.format(price) + " ₽";
    }

    public String getFormattedDiscountedPrice() {
        if (hasDiscount && discountedPrice != null) {
            return PriceFormatter.format(discountedPrice) + " ₽";
        }
        return getFormattedPrice();
    }

    public boolean isHasDiscount() {
        return hasDiscount || (discountPercentage != null && discountPercentage.compareTo(BigDecimal.ZERO) > 0);
    }
}
