package ru.fisher.ToolsMarket.dto.OrderDTO;

import ru.fisher.ToolsMarket.dto.SimpleOrderItemDto;

import java.math.BigDecimal;
import java.util.List;

public record OrderEmailPayload(
        Long orderId,
        Long orderNumber,
        List<SimpleOrderItemDto> items,
        BigDecimal total,
        String customerEmail,
        String note
) {}
