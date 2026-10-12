package ru.fisher.ToolsMarket.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.fisher.ToolsMarket.dto.ImportResult;
import ru.fisher.ToolsMarket.dto.PriceChange;
import ru.fisher.ToolsMarket.dto.PriceRow;
import ru.fisher.ToolsMarket.models.Product;
import ru.fisher.ToolsMarket.repository.ProductRepository;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class PriceImportService {

    private final PriceExcelParser excelParser;
    private final ProductRepository productRepository;

    @Value("${app.import.batch-size}")
    private int batchSize;

    /**
     * Реальный импорт с сохранением в БД
     */
    @Transactional
    public ImportResult importPrices(InputStream is, String filename) throws IOException {
        long startTime = System.currentTimeMillis();

        List<PriceRow> rows = excelParser.parse(is);
        log.info("Обычный импорт цен для {} товаров", rows.size());

        Map<String, Product> productMap = loadProducts(rows, productRepository::findAllBySkus);

        List<Product> productsToUpdate = new ArrayList<>();
        // Импорт — единственное, что делает с найденным изменением: применяет цену
        // и копит товары для пакетного сохранения. Само поведение передаётся сюда.
        PriceScan scan = scan(rows, productMap, (product, row) -> {
            product.setPrice(row.price());
            product.setUpdatedAt(Instant.now());
            productsToUpdate.add(product);
        });

        if (!productsToUpdate.isEmpty()) {
            // Сохраняем пакетами
            for (int i = 0; i < productsToUpdate.size(); i += batchSize) {
                int end = Math.min(i + batchSize, productsToUpdate.size());
                productRepository.saveAll(productsToUpdate.subList(i, end));
            }
        }

        long endTime = System.currentTimeMillis();
        log.info("Обычный импорт завершен за {} мс. Обновлено: {}, без изменений: {}, не найдено: {}",
                (endTime - startTime), scan.changes().size(), scan.samePrice(), scan.notFound().size());

        return new ImportResult(
                scan.changes().size(), scan.samePrice(), scan.notFound().size(),
                scan.notFound(), scan.changes(), filename, false
        );
    }

    /**
     * Dry-run: только проверка без сохранения
     */
    @Transactional(readOnly = true)
    public ImportResult dryRunImport(InputStream is, String filename) throws IOException {
        long startTime = System.currentTimeMillis();

        List<PriceRow> rows = excelParser.parse(is);
        log.info("Dry-run для {} товаров", rows.size());

        Map<String, Product> productMap = loadProducts(rows, productRepository::findAllBySkusOptimized);

        // Предпросмотр ничего не меняет — поведения на изменение нет.
        PriceScan scan = scan(rows, productMap, null);

        long endTime = System.currentTimeMillis();
        log.info("Dry-run завершен за {} мс. Будет обновлено: {}, без изменений: {}, не найдено: {}",
                (endTime - startTime), scan.changes().size(), scan.samePrice(), scan.notFound().size());

        return ImportResult.dryRun(scan.changes(), scan.notFound(), scan.samePrice(), filename);
    }

    /**
     * Полиморфный единый проход по строкам прайса. Сравнение цен и сбор изменений
     * описаны один раз, а что делать с найденным изменением — параметр
     * ({@link BiConsumer}); {@code null} означает «только посчитать» (dry-run).
     * Загрузчик товаров тоже параметр, поэтому import и dry-run могут ходить в БД
     * разными запросами, не дублируя цикл.
     */
    private PriceScan scan(List<PriceRow> rows, Map<String, Product> productMap,
                           BiConsumer<Product, PriceRow> onChanged) {
        List<PriceChange> changes = new ArrayList<>();
        int samePrice = 0;
        List<String> notFound = new ArrayList<>();

        for (PriceRow row : rows) {
            Product product = productMap.get(row.sku());

            if (product == null) {
                notFound.add(row.sku());
                continue;
            }

            if (product.getPrice().compareTo(row.price()) != 0) {
                changes.add(new PriceChange(
                        row.sku(), product.getTitle(),
                        product.getPrice(), row.price()
                ));
                if (onChanged != null) {
                    onChanged.accept(product, row);
                }
            } else {
                samePrice++;
            }
        }

        return new PriceScan(changes, samePrice, notFound);
    }

    private Map<String, Product> loadProducts(List<PriceRow> rows,
                                              Function<Set<String>, List<Product>> loader) {
        Set<String> skus = rows.stream().map(PriceRow::sku).collect(Collectors.toSet());
        return loader.apply(skus).stream()
                .collect(Collectors.toMap(Product::getSku, p -> p));
    }

    private record PriceScan(List<PriceChange> changes, int samePrice, List<String> notFound) {
    }
}
