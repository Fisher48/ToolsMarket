package ru.fisher.ToolsMarket.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.fisher.ToolsMarket.dto.ImportResult;
import ru.fisher.ToolsMarket.dto.PriceRow;
import ru.fisher.ToolsMarket.models.Product;
import ru.fisher.ToolsMarket.repository.ProductRepository;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class PriceImportService {

    private final PriceExcelParser excelParser;
    private final ProductRepository productRepository;

    @Transactional
    public ImportResult importPrices(InputStream is, String filename) throws IOException {
        List<PriceRow> rows = excelParser.parse(is);

        int updated = 0;
        int samePrice = 0;
        List<String> notFound = new ArrayList<>();

        for (PriceRow row : rows) {
            Optional<Product> productOpt = productRepository.findBySku(row.sku());

            if (productOpt.isPresent()) {
                Product product = productOpt.get();

                // Проверяем, изменилась ли цена
                if (product.getPrice().compareTo(row.price()) != 0) {
                    // Обновляем цену
                    product.setPrice(row.price());
                    product.setUpdatedAt(Instant.now());
                    productRepository.save(product);

                    updated++;
                    log.info("Updated price for {}: {} -> {}",
                            row.sku(), product.getPrice(), row.price());
                } else {
                    samePrice++;
                }
            } else {
                notFound.add(row.sku());
                log.warn("Product with SKU {} not found", row.sku());
            }
        }

        return new ImportResult(updated, notFound, samePrice, filename);
    }
}
