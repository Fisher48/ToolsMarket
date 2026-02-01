package ru.fisher.ToolsMarket.dto;

import java.math.BigDecimal;

public record SimpleOrderItemDto(
        String productName,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal
) {}