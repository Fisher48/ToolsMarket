package ru.fisher.ToolsMarket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.fisher.ToolsMarket.util.PriceFormatter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductListDto {
    private Long id;
    private String name;
    private String title;
    private String shortDescription;
    private String sku;
    private BigDecimal price;
    private String currency;
    private boolean active;
    private List<ProductImageDto> images;
    private String mainImageUrl;
    private Instant createdAt;

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
}
