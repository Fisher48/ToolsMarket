package ru.fisher.ToolsMarket.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Инварианты канонического округления скидок: округление ровно один раз — на
 * единицу товара (см. {@link DiscountMath}), дальше только умножения. Эти
 * инварианты держат корзину и сохранённый в заказе OrderItem в одних копейках.
 */
class DiscountMathTest {

    private static final BigDecimal PRICE = new BigDecimal("33.33");
    private static final BigDecimal PCT = new BigDecimal("10");
    private static final long QTY = 3;

    @Test
    void unitDiscountRoundsOncePerUnitHalfUp() {
        // 33.33 × 0.1 = 3.333 → 3.33
        assertThat(DiscountMath.unitDiscount(PRICE, PCT))
                .isEqualByComparingTo(new BigDecimal("3.33"));
    }

    @Test
    void unitPriceWithDiscountIsPriceMinusUnitDiscount() {
        assertThat(DiscountMath.unitPriceWithDiscount(PRICE, PCT))
                .isEqualByComparingTo(new BigDecimal("30.00"));
    }

    @Test
    void lineTotalIsUnitPriceWithDiscountTimesQuantity() {
        // Раньше корзина показывала 89,99, а заказ сохранял 90,00 — теперь единый канон.
        assertThat(DiscountMath.lineTotalWithDiscount(PRICE, PCT, QTY))
                .isEqualByComparingTo(new BigDecimal("90.00"));
    }

    @Test
    void lineItemsConserveMoney() {
        // сумма позиции − скидка позиции == итог позиции, без потерянных копеек
        BigDecimal gross = PRICE.multiply(BigDecimal.valueOf(QTY));
        BigDecimal discount = DiscountMath.lineDiscount(PRICE, PCT, QTY);
        BigDecimal total = DiscountMath.lineTotalWithDiscount(PRICE, PCT, QTY);

        assertThat(gross.subtract(discount)).isEqualByComparingTo(total);
        assertThat(discount).isEqualByComparingTo(new BigDecimal("9.99"));
    }

    @Test
    void alternativeFormulaWouldDifferOnRounding() {
        // цена × (100 − pct) / 100 для 0.05 и 10% даёт 0.045 → 0.05,
        // а канон «цена − скидка на единицу» — 0.04. Канон согласован с OrderItem.
        BigDecimal price = new BigDecimal("0.05");
        BigDecimal pct = new BigDecimal("10");

        assertThat(DiscountMath.unitPriceWithDiscount(price, pct))
                .isEqualByComparingTo(new BigDecimal("0.04"));

        BigDecimal alternative = price.multiply(new BigDecimal("90"))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        assertThat(alternative).isEqualByComparingTo(new BigDecimal("0.05"));
    }

    @Test
    void hasDiscountOnlyForPositivePercentage() {
        assertThat(DiscountMath.hasDiscount(new BigDecimal("0.01"))).isTrue();
        assertThat(DiscountMath.hasDiscount(new BigDecimal("10"))).isTrue();
        assertThat(DiscountMath.hasDiscount(null)).isFalse();
        assertThat(DiscountMath.hasDiscount(BigDecimal.ZERO)).isFalse();
        assertThat(DiscountMath.hasDiscount(new BigDecimal("-5"))).isFalse();
    }

    @Test
    void noDiscountKeepsPriceUntouched() {
        assertThat(DiscountMath.lineTotalWithDiscount(PRICE, null, QTY))
                .isEqualByComparingTo(new BigDecimal("99.99"));
        assertThat(DiscountMath.unitPriceWithDiscount(PRICE, BigDecimal.ZERO))
                .isEqualByComparingTo(PRICE);
        assertThat(DiscountMath.unitDiscount(PRICE, null))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void nullPriceNeverThrows() {
        assertThat(DiscountMath.unitDiscount(null, PCT))
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(DiscountMath.unitPriceWithDiscount(null, PCT))
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(DiscountMath.lineDiscount(null, PCT, QTY))
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(DiscountMath.lineTotalWithDiscount(null, PCT, QTY))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void otherPercentagesStayConsistentAcrossQuantity() {
        BigDecimal price = new BigDecimal("19.99");
        BigDecimal pct = new BigDecimal("33.33");

        assertThat(DiscountMath.unitDiscount(price, pct))
                .isEqualByComparingTo(new BigDecimal("6.66"));
        assertThat(DiscountMath.unitPriceWithDiscount(price, pct))
                .isEqualByComparingTo(new BigDecimal("13.33"));

        for (long qty = 1; qty <= 4; qty++) {
            BigDecimal gross = price.multiply(BigDecimal.valueOf(qty));
            BigDecimal discount = DiscountMath.lineDiscount(price, pct, qty);
            BigDecimal total = DiscountMath.lineTotalWithDiscount(price, pct, qty);
            assertThat(gross.subtract(discount)).isEqualByComparingTo(total);
        }
    }
}