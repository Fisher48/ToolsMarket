package ru.fisher.ToolsMarket.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ImportResult(
        int updatedCount,               // сколько товаров будет обновлено
        int samePriceCount,             // сколько товаров с той же ценой
        int notFoundCount,              // сколько артикулов не найдено
        List<String> notFoundArticles,  // не найденные артикулы
        List<PriceChange> priceChanges, // детали изменений цен
        String filename,                // имя файла
        LocalDateTime importedAt,       // время импорта
        boolean dryRun                  // был ли это dry-run
) {
    public ImportResult(int updatedCount, List<String> notFoundArticles,
                        int samePriceCount, String filename) {
        this(updatedCount, samePriceCount, notFoundArticles.size(), notFoundArticles,
                List.of(), filename, LocalDateTime.now(), false);
    }

    public ImportResult(int updatedCount, int samePriceCount, int notFoundCount,
                        List<String> notFoundArticles, List<PriceChange> priceChanges,
                        String filename, boolean dryRun) {
        this(updatedCount, samePriceCount, notFoundCount, notFoundArticles,
                priceChanges, filename, LocalDateTime.now(), dryRun);
    }

    public static ImportResult dryRun(List<PriceChange> changes,
                                      List<String> notFound,
                                      int samePriceCount,
                                      String filename) {
        return new ImportResult(
                changes.size(),
                samePriceCount,
                notFound.size(),
                notFound,
                changes,
                filename,
                true
        );
    }
}
