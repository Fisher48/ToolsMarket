package ru.fisher.ToolsMarket.dto;

import java.math.BigDecimal;

public record PriceChange(
        String sku,
        String productName,
        BigDecimal oldPrice,
        BigDecimal newPrice,
        BigDecimal difference
) {
    public PriceChange(String sku, String productName,
                       BigDecimal oldPrice, BigDecimal newPrice) {
        this(sku, productName, oldPrice, newPrice,
                newPrice.subtract(oldPrice));
    }

    public boolean isPriceIncreased() {
        return difference.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean isPriceDecreased() {
        return difference.compareTo(BigDecimal.ZERO) < 0;
    }
}
