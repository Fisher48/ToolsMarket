package ru.fisher.ToolsMarket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.fisher.ToolsMarket.util.PriceFormatter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductDto {
    private Long id;
    private String name;
    private String title;
    private String shortDescription;
    private String description;
    private String sku;
    private BigDecimal price;
    private String currency;
    private boolean active;
    private Set<CategorySimpleDto> categories;
    private List<ProductImageDto> images;
    private Instant createdAt;
    private Instant updatedAt;

    // Дополнительные вычисляемые поля для UI
    public String getFormattedPrice() {
        return PriceFormatter.format(price) + " " + getCurrencySymbol();
    }

    private String getCurrencySymbol() {
        return switch (currency.toUpperCase()) {
            case "RUB", "RUR" -> "₽";
            case "USD" -> "$";
            case "EUR" -> "€";
            default -> currency;
        };
    }

    public String getMainImageUrl() {
        return images != null && !images.isEmpty() ? images.getFirst().getUrl() : "/images/placeholder.jpg";
    }
}
