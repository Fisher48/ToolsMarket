package ru.fisher.ToolsMarket.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ImportResult(
        int updatedCount,      // сколько товаров обновлено
        List<String> notFoundArticles, // не найденные артикулы
        int samePriceCount,    // сколько товаров с той же ценой
        String filename,       // имя файла
        LocalDateTime importedAt // время импорта
) {
    public ImportResult(int updatedCount, List<String> notFoundArticles,
                        int samePriceCount, String filename) {
        this(updatedCount, notFoundArticles, samePriceCount,
                filename, LocalDateTime.now());
    }
}
