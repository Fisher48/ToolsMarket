package ru.fisher.ToolsMarket.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import ru.fisher.ToolsMarket.dto.PriceRow;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class PriceExcelParser {

//    static {
//        // Устанавливаем перед использованием POI
//        ZipSecureFile.setMinInflateRatio(0.005);
//    }

    public List<PriceRow> parse(InputStream inputStream) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            List<PriceRow> allRows = new ArrayList<>();

            // Проверяем ВСЕ листы
            for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
                Sheet sheet = workbook.getSheetAt(sheetIndex);
                String sheetName = sheet.getSheetName();

                log.debug("Processing sheet: {} (index: {})", sheetName, sheetIndex);

                List<PriceRow> sheetRows = parseSheet(sheet);
                allRows.addAll(sheetRows);

                log.info("Sheet '{}': found {} valid rows", sheetName, sheetRows.size());
            }

            log.info("Total rows parsed from all sheets: {}", allRows.size());
            return allRows;
        }
    }

    private Sheet findTargetSheet(Workbook workbook) {
        // Приоритетные названия листов
        String[] targetNames = {"Prices", "Прайс", "Price", "Товары", "Products", "Прайс-лист"};

        for (String targetName : targetNames) {
            Sheet sheet = workbook.getSheet(targetName);
            if (sheet != null) {
                return sheet;
            }
        }

        // Ищем по частичному совпадению
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            String sheetName = sheet.getSheetName().toLowerCase();

            if (sheetName.contains("price") ||
                    sheetName.contains("прайс") ||
                    sheetName.contains("товар") ||
                    sheetName.contains("product")) {
                return sheet;
            }
        }

        return null;
    }

    private List<PriceRow> parseSheet(Sheet sheet) {
        int articleCol = -1;
        int priceCol = -1;
        int headerRowIndex = -1;

        // Ищем строку заголовков
        for (Row row : sheet) {
            for (Cell cell : row) {
                if (cell.getCellType() == CellType.STRING) {
                    String value = cell.getStringCellValue().toLowerCase();

                    if (value.contains("артикул") || value.contains("sku") || value.contains("код")) {
                        articleCol = cell.getColumnIndex();
                    }
                    if (value.contains("прайс") || value.contains("цена") || value.contains("price")) {
                        priceCol = cell.getColumnIndex();
                    }
                }
            }

            if (articleCol != -1 && priceCol != -1) {
                headerRowIndex = row.getRowNum();
                break;
            }
        }

        // Если не нашли заголовки в этом листе
        if (headerRowIndex == -1) {
            log.debug("Sheet '{}': no headers found, skipping", sheet.getSheetName());
            return List.of();
        }

        // Читаем строки товаров
        List<PriceRow> result = new ArrayList<>();

        for (int i = headerRowIndex + 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;

            String article = getString(row.getCell(articleCol));
            BigDecimal price = getPrice(row.getCell(priceCol));

            if (article == null || price == null) {
                continue; // не товар
            }

            result.add(new PriceRow(article, price));
        }

        return result;
    }

    private String getString(Cell cell) {
        if (cell == null) return null;

        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            default -> null;
        };
    }

    private BigDecimal getPrice(Cell cell) {
        if (cell == null) return null;

        if (cell.getCellType() == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue());
        }

        if (cell.getCellType() == CellType.STRING) {
            String raw = cell.getStringCellValue()
                    .replace("\u00A0", "") // неразрывный пробел
                    .replace(" ", "")
                    .replace(",", ".")
                    .trim();

            if (raw.isEmpty()) return null;

            try {
                return new BigDecimal(raw);
            } catch (NumberFormatException e) {
                return null;
            }
        }

        return null;
    }
}
