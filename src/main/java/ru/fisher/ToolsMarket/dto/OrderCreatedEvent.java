package ru.fisher.ToolsMarket.dto;

import ru.fisher.ToolsMarket.models.OrderItem;

import java.math.BigDecimal;
import java.util.List;

public record OrderCreatedEvent(
        Long orderId,
        Long orderNumber,
        List<OrderItem> orderItems,
        BigDecimal total,
        String customerEmail
) {}
