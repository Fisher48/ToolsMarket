package ru.fisher.ToolsMarket.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class CartItemDto {
    private Long productId;
    private String productName;
    private String productSku;
    private BigDecimal unitPrice;
    private Integer quantity;
    private BigDecimal totalPrice;

    public CartItemDto(Long productId, String productName, String productSku,
                       BigDecimal unitPrice, Integer quantity) {
        this.productId = productId;
        this.productName = productName;
        this.productSku = productSku;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.totalPrice = unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
