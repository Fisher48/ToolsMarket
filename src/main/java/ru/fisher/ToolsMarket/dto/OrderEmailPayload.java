package ru.fisher.ToolsMarket.dto;

import java.math.BigDecimal;
import java.util.List;

public record OrderEmailPayload(
        Long orderId,
        Long orderNumber,
        List<SimpleOrderItemDto> items,
        BigDecimal total,
        String customerEmail
) {}
