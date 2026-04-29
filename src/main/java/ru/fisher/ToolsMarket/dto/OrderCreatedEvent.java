package ru.fisher.ToolsMarket.dto;


import java.math.BigDecimal;
import java.util.List;

public record OrderCreatedEvent(
        Long orderId,
        Long orderNumber,
        List<OrderItemDto> orderItems,
        BigDecimal total,
        String customerEmail,
        String note
) {}
