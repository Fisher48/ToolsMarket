package ru.fisher.ToolsMarket.dto;

import java.util.Objects;

public record CartRequest(Long productId, Integer quantity) {
    // Конструктор с валидацией
    public CartRequest {
        Objects.requireNonNull(productId, "productId must not be null");
        if (quantity != null && quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
    }

    // Для удобства
    public int getQuantityOrDefault() {
        return quantity != null ? quantity : 1;
    }
}
