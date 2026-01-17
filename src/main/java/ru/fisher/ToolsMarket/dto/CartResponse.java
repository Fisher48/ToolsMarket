package ru.fisher.ToolsMarket.dto;

import java.util.List;
import java.util.Map;

public record CartResponse(
        List<CartItemDto> items,
        int totalQuantity,
        Map<Long, Integer> quantities,
        double totalAmount,          // Общая сумма без скидок
        double totalDiscount,        // Общая сумма скидок
        double totalWithDiscount     // Итоговая сумма со скидками
) {}
