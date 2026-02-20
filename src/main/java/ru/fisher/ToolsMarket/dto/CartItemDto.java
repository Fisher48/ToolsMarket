package ru.fisher.ToolsMarket.dto;

import lombok.*;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CartItemDto {
    private Long productId;
    private String productName;
    private String productSku;
    private String productTitle;
    private String productImageUrl;
    private String productImageAlt;
    private BigDecimal unitPrice;
    private BigDecimal unitPriceWithDiscount;
    private Integer quantity;
    private BigDecimal totalPrice;
    private BigDecimal totalPriceWithDiscount;
    private BigDecimal discountAmount;
    private BigDecimal discountPercentage;

    // Конструктор для обратной совместимости
    public CartItemDto(Long productId, String productName, String productSku,
                       String productTitle, String productImageUrl, String productImageAlt,
                       BigDecimal unitPrice, Integer quantity) {
        this.productId = productId;
        this.productName = productName;
        this.productSku = productSku;
        this.productTitle = productTitle;
        this.productImageUrl = productImageUrl;
        this.productImageAlt = productImageAlt;
        this.unitPrice = unitPrice;
        this.quantity = quantity;

        // Рассчитываем значения по умолчанию
        this.unitPriceWithDiscount = unitPrice;
        this.totalPrice = calculateTotalPrice();
        this.totalPriceWithDiscount = this.totalPrice;
        this.discountAmount = BigDecimal.ZERO;
        this.discountPercentage = BigDecimal.ZERO;
    }

    private BigDecimal calculateTotalPrice() {
        if (unitPrice != null && quantity != null) {
            return unitPrice.multiply(BigDecimal.valueOf(quantity));
        }
        return BigDecimal.ZERO;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice != null ? totalPrice : calculateTotalPrice();
    }

    public BigDecimal getTotalPriceWithDiscount() {
        if (totalPriceWithDiscount != null) {
            return totalPriceWithDiscount;
        }

        BigDecimal total = getTotalPrice();
        if (discountAmount != null && discountAmount.compareTo(BigDecimal.ZERO) > 0) {
            return total.subtract(discountAmount);
        }
        return total;
    }

    /**
     * Получить цену за единицу товара со скидкой
     * Если поле не заполнено, вычисляет из общей суммы со скидкой
     */
    public BigDecimal getUnitPriceWithDiscount() {
        if (unitPriceWithDiscount != null) {
            return unitPriceWithDiscount;
        }

        // Если есть общая сумма со скидкой и количество, вычисляем
        if (getTotalPriceWithDiscount() != null && quantity != null && quantity > 0) {
            return getTotalPriceWithDiscount()
                    .divide(BigDecimal.valueOf(quantity), 2, RoundingMode.HALF_UP);
        }

        // Если ничего нет, возвращаем обычную цену
        return unitPrice;
    }

    public BigDecimal getDiscountAmount() {
        if (discountAmount != null) {
            return discountAmount;
        }

        if (discountPercentage != null && discountPercentage.compareTo(BigDecimal.ZERO) > 0 &&
                unitPrice != null && quantity != null) {
            return unitPrice
                    .multiply(discountPercentage.divide(BigDecimal.valueOf(100)))
                    .multiply(BigDecimal.valueOf(quantity))
                    .setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }
}
