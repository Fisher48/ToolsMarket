package ru.fisher.ToolsMarket.util;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class PriceFormatter {

    private static final DecimalFormat PRICE_FORMAT;

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.forLanguageTag("ru-RU"));
        symbols.setGroupingSeparator(' ');
        symbols.setDecimalSeparator(',');

        PRICE_FORMAT = new DecimalFormat("#,###", symbols);
        PRICE_FORMAT.setGroupingUsed(true);
        PRICE_FORMAT.setGroupingSize(3);
    }

    public static String format(BigDecimal price) {
        if (price == null) {
            return "0";
        }
        // Округляем до целых рублей
        BigDecimal rounded = price.setScale(0, java.math.RoundingMode.HALF_UP);
        return PRICE_FORMAT.format(rounded);
    }

    public static String formatWithCurrency(BigDecimal price, String currency) {
        String formattedPrice = format(price);
        return formattedPrice + " " + getCurrencySymbol(currency);
    }

    private static String getCurrencySymbol(String currency) {
        return switch (currency.toUpperCase()) {
            case "RUB", "RUR" -> "₽";
            case "USD" -> "$";
            case "EUR" -> "€";
            default -> currency;
        };
    }
}
