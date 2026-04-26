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

        Set<String> skus = rows.stream().map(PriceRow::sku).collect(Collectors.toSet());
        Map<String, Product> productMap = productRepository.findAllBySkus(skus).stream()
                .collect(Collectors.toMap(Product::getSku, p -> p));

        List<PriceChange> changes = new ArrayList<>();
        int samePrice = 0;
        List<String> notFound = new ArrayList<>();
        List<Product> productsToUpdate = new ArrayList<>();

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
                product.setPrice(row.price());
                product.setUpdatedAt(Instant.now());
                productsToUpdate.add(product);
            } else {
                samePrice++;
            }
        }

        if (!productsToUpdate.isEmpty()) {
            // Сохраняем пакетами
            for (int i = 0; i < productsToUpdate.size(); i += batchSize) {
                int end = Math.min(i + batchSize, productsToUpdate.size());
                productRepository.saveAll(productsToUpdate.subList(i, end));
            }
        }

        long endTime = System.currentTimeMillis();
        log.info("Обычный импорт завершен за {} мс. Обновлено: {}, без изменений: {}, не найдено: {}",
                (endTime - startTime), changes.size(), samePrice, notFound.size());

        return new ImportResult(
                changes.size(), samePrice, notFound.size(),
                notFound, changes, filename, false
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

        Set<String> skus = rows.stream().map(PriceRow::sku).collect(Collectors.toSet());
        Map<String, Product> productMap = productRepository.findAllBySkusOptimized(skus).stream()
                .collect(Collectors.toMap(Product::getSku, p -> p));

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
            } else {
                samePrice++;
            }
        }

        long endTime = System.currentTimeMillis();
        log.info("Dry-run завершен за {} мс. Будет обновлено: {}, без изменений: {}, не найдено: {}",
                (endTime - startTime), changes.size(), samePrice, notFound.size());

        return ImportResult.dryRun(changes, notFound, samePrice, filename);
    }
}
