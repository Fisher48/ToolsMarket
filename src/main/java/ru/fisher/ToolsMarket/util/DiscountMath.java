package ru.fisher.ToolsMarket.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Единая арифметика скидок. Канон: округление выполняется ровно один раз — на
 * единицу товара ({@link #unitDiscount}), дальше идут только умножения, которые
 * округления не меняют. Поэтому всегда выполняется:
 * <pre>
 *   итог позиции = (цена единицы − скидка на единицу) × количество
 *   скидка позиции = скидка на единицу × количество
 *   сумма корзины − суммарная скидка == итог к оплате (без потерянных копеек)
 * </pre>
 * Альтернативные формулы (цена × (100−процент) / 100 или округление суммы позиции)
 * в редких случаях дают иную копейку и до введения этого класса разъезжались с
 * сохранённым в заказе `OrderItem` — это была причина расхождений корзины и заказа.
 */
public final class DiscountMath {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int SCALE = 2;
    private static final RoundingMode MODE = RoundingMode.HALF_UP;

    private DiscountMath() {
    }

    public static boolean hasDiscount(BigDecimal percentage) {
        return percentage != null && percentage.signum() > 0;
    }

    /**
     * Скидка на одну единицу товара — единственное место в проекте, где
     * происходит округление денежных сумм по скидке.
     */
    public static BigDecimal unitDiscount(BigDecimal unitPrice, BigDecimal percentage) {
        if (unitPrice == null || !hasDiscount(percentage)) {
            return BigDecimal.ZERO.setScale(SCALE, MODE);
        }
        return unitPrice.multiply(percentage).divide(HUNDRED, SCALE, MODE);
    }

    /**
     * Цена единицы товара со скидкой: цена − скидка на единицу.
     * НЕ вычисляется как цена × (100 − процент) / 100 — это даёт другую копейку.
     */
    public static BigDecimal unitPriceWithDiscount(BigDecimal unitPrice, BigDecimal percentage) {
        if (unitPrice == null) {
            return BigDecimal.ZERO.setScale(SCALE, MODE);
        }
        if (!hasDiscount(percentage)) {
            return unitPrice;
        }
        return unitPrice.subtract(unitDiscount(unitPrice, percentage));
    }

    /**
     * Скидка по позиции: скидка на единицу × количество.
     */
    public static BigDecimal lineDiscount(BigDecimal unitPrice, BigDecimal percentage, long quantity) {
        return unitDiscount(unitPrice, percentage).multiply(BigDecimal.valueOf(quantity));
    }

    /**
     * Итог позиции со скидкой: (цена − скидка на единицу) × количество.
     */
    public static BigDecimal lineTotalWithDiscount(BigDecimal unitPrice, BigDecimal percentage, long quantity) {
        return unitPriceWithDiscount(unitPrice, percentage).multiply(BigDecimal.valueOf(quantity));
    }
}