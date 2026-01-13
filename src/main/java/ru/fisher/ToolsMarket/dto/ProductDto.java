package ru.fisher.ToolsMarket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.fisher.ToolsMarket.models.ProductType;
import ru.fisher.ToolsMarket.util.PriceFormatter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
    // Новые поля для характеристик
    private List<ProductAttributeValueDto> attributeValues;
    private Map<String, String> specifications; // Упрощенная версия для шаблонов

    // НОВОЕ: Поля для скидок
    private ProductType productType;
    private BigDecimal discountPercentage;
    private BigDecimal discountedPrice;
    private boolean hasDiscount;

    // Метод для получения цены со скидкой
    public BigDecimal getDiscountedPrice() {
        if (discountedPrice != null) {
            return discountedPrice;
        }
        if (discountPercentage != null && discountPercentage.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal discountMultiplier = BigDecimal.ONE
                    .subtract(discountPercentage.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
            return price.multiply(discountMultiplier)
                    .setScale(2, RoundingMode.HALF_UP);
        }
        return price;
    }

    // Проверка наличия скидки
    public boolean hasDiscount() {
        return hasDiscount || (discountPercentage != null && discountPercentage.compareTo(BigDecimal.ZERO) > 0);
    }

    // Форматированная цена со скидкой
    public String getFormattedDiscountedPrice() {
        if (hasDiscount()) {
            return PriceFormatter.format(getDiscountedPrice()) + " " + getCurrencySymbol();
        }
        return null;
    }

    // HTML для отображения цены
    public String getDisplayPriceHtml() {
        if (hasDiscount()) {
            return String.format(
                    "<span class=\"text-muted text-decoration-line-through me-2\">%s</span>" +
                            "<span class=\"h2 text-danger\">%s</span>" +
                            "<span class=\"badge bg-danger ms-2\">-%s%%</span>",
                    getFormattedPrice(),
                    getFormattedDiscountedPrice(),
                    discountPercentage.setScale(0, RoundingMode.HALF_UP)
            );
        }
        return String.format("<span class=\"h2 text-primary\">%s</span>", getFormattedPrice());
    }


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

    // Метод для получения значения конкретной характеристики
    public String getAttributeValue(String attributeName) {
        if (attributeValues != null) {
            return attributeValues.stream()
                    .filter(av -> attributeName.equals(av.getAttributeName()))
                    .map(ProductAttributeValueDto::getValue)
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    // Метод для проверки наличия характеристик
    public boolean hasSpecifications() {
        return (attributeValues != null && !attributeValues.isEmpty()) ||
                (specifications != null && !specifications.isEmpty());
    }
}
