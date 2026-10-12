package ru.fisher.ToolsMarket.dto.CartDTO;

import java.math.BigDecimal;

/**
 * Наименее мощный интерфейс позиции корзины/заказа: ровно то, что нужно для
 * подсчёта суммы. Методы, которым нужна только сумма, зависят от этой абстракции,
 * а не от конкретного {@link CartItemDto}, поэтому продолжают работать с любой
 * будущей реализацией позиции.
 */
public interface PricedLine {

    BigDecimal getUnitPrice();

    Integer getQuantity();
}
