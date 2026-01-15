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

    // НОВОЕ: Поля для скидок
    private ProductType productType;
    private BigDecimal discountPercentage;
    private BigDecimal discountedPrice;
    private boolean hasDiscount;

    private boolean inCart;
    private int cartQuantity;

    // Метод для установки информации о корзине
    public void setCartInfo(Map<Long, Integer> cartProductQuantities) {
        Integer quantity = cartProductQuantities.get(this.id);
        this.inCart = quantity != null && quantity > 0;
        this.cartQuantity = quantity != null ? quantity : 0;
    }

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

    // HTML для отображения цены в списках
    public String getDisplayPriceHtml() {
        if (hasDiscount()) {
            return String.format(
                    "<div class=\"d-flex align-items-center mb-1\">" +
                            "<span class=\"text-muted text-decoration-line-through me-2\">%s</span>" +
                            "<span class=\"fw-bold text-danger\">%s</span>" +
                            "</div>" +
                            "<div><span class=\"badge bg-danger small\">-%s%%</span></div>",
                    getFormattedPrice(),
                    getFormattedDiscountedPrice(),
                    discountPercentage.setScale(0, RoundingMode.HALF_UP)
            );
        }
        return String.format("<span class=\"fw-bold text-primary\">%s</span>", getFormattedPrice());
    }

    // Метод для отображения цены в карточке товара
    public String getCardPriceHtml() {
        if (hasDiscount()) {
            return "<div class=\"d-flex align-items-center\">" +
                    "<span class=\"text-muted text-decoration-line-through me-2 small\">" +
                    getFormattedPrice() + "</span>" +
                    "<span class=\"h6 text-danger fw-bold mb-0\">" +
                    getFormattedDiscountedPrice() + "</span>" +
                    "</div>" +
                    "<div class=\"mt-1\">" +
                    "<span class=\"badge bg-danger small\">-" +
                    discountPercentage.setScale(0, RoundingMode.HALF_UP) + "%</span>" +
                    "</div>";
        }
        return "<span class=\"h6 text-primary fw-bold mb-0\">" + getFormattedPrice() + "</span>";
    }

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

    // Метод для получения URL главного изображения
    public String getMainImageUrl() {
        if (mainImageUrl != null && !mainImageUrl.isEmpty()) {
            return mainImageUrl;
        }
        if (images != null && !images.isEmpty()) {
            return images.getFirst().getUrl();
        }
        return "/images/placeholder.jpg";
    }
}
