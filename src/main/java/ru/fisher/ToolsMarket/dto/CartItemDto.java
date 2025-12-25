package ru.fisher.ToolsMarket.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@RequiredArgsConstructor
public class CartItemDto {
    private Long productId;
    private String productName;
    private String productSku;
    private String productTitle; // для URL страницы товара
    private String productImageUrl; // ← URL основного изображения
    private String productImageAlt; // ← alt текст изображения (опционально)
    private BigDecimal unitPrice;
    private Integer quantity;
    private BigDecimal totalPrice;

    // Конструктор с вычислением totalPrice
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
        // Гарантируем, что totalPrice не будет null
        this.totalPrice = (unitPrice != null && quantity != null)
                ? unitPrice.multiply(BigDecimal.valueOf(quantity))
                : BigDecimal.ZERO;
    }

    // Геттер с проверкой
    public BigDecimal getTotalPrice() {
        if (totalPrice == null) {
            if (unitPrice != null && quantity != null) {
                return unitPrice.multiply(BigDecimal.valueOf(quantity));
            }
            return BigDecimal.ZERO;
        }
        return totalPrice;
    }
}
