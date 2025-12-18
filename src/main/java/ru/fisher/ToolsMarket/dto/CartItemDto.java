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
    private BigDecimal unitPrice;
    private Integer quantity;
    private BigDecimal totalPrice;

    // Конструктор с вычислением totalPrice
    public CartItemDto(Long productId, String productName, String productSku,
                       BigDecimal unitPrice, Integer quantity) {
        this.productId = productId;
        this.productName = productName;
        this.productSku = productSku;
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
