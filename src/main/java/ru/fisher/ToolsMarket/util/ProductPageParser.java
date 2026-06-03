package ru.fisher.ToolsMarket.util;

import ru.fisher.ToolsMarket.dto.ParsedProductData;

public interface ProductPageParser {
    /**
     * Проверяет, подходит ли этот парсер для данного URL.
     */
    boolean supports(String url);

    /**
     * Парсит страницу товара и возвращает структурированные данные.
     */
    ParsedProductData parse(String url) throws Exception;
}
