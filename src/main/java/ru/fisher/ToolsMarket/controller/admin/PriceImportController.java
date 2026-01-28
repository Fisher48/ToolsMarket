package ru.fisher.ToolsMarket.controller.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.fisher.ToolsMarket.dto.ImportResult;
import ru.fisher.ToolsMarket.service.PriceImportService;

import java.io.IOException;
import java.util.List;

@Controller
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/admin/prices")
public class PriceImportController {

    private final PriceImportService priceImportService;

    @GetMapping
    public String importPage(Model model) {
        model.addAttribute("result", null);
        model.addAttribute("dryRun", false);
        return "admin/prices/import_prices";
    }

    @PostMapping("/import")
    public String handleImport(@RequestParam("file") MultipartFile file,
                               @RequestParam(value = "dryRun", defaultValue = "false") boolean dryRun,
                               Model model) throws IOException {

        log.info("Price import request: file={}, size={}, dryRun={}",
                file.getOriginalFilename(), file.getSize(), dryRun);

        // Валидация файла
        if (file.isEmpty()) {
            model.addAttribute("error", "Файл пустой");
            return "admin/prices/import_prices";
        }

        if (!isValidExcelFile(file)) {
            model.addAttribute("error",
                    "Поддерживаются только файлы Excel (.xlsx, .xls)");
            return "admin/prices/import_prices";
        }

        try {
            ImportResult result;

            if (dryRun) {
                // Тестовый режим
                result = priceImportService.dryRunImport(
                        file.getInputStream(),
                        file.getOriginalFilename()
                );
                log.info("Dry-run completed: {} changes, {} not found",
                        result.updatedCount(), result.notFoundCount());
            } else {
                // Реальный импорт
                result = priceImportService.importPrices(
                        file.getInputStream(),
                        file.getOriginalFilename()
                );
                log.info("Import completed: {} updated, {} not found",
                        result.updatedCount(), result.notFoundCount());
            }

            model.addAttribute("result", result);
            model.addAttribute("dryRun", dryRun);

        } catch (Exception e) {
            log.error("Error importing prices", e);
            model.addAttribute("error",
                    "Ошибка обработки файла: " + e.getMessage());
        }

        return "admin/prices/import_prices";
    }

    /**
     * Отдельный endpoint для быстрого dry-run через AJAX
     */
    @PostMapping("/dry-run")
    @ResponseBody
    public ResponseEntity<ImportResult> dryRun(@RequestParam("file") MultipartFile file)
            throws IOException {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ImportResult.dryRun(List.of(), List.of("Empty file"), 0, ""));
        }

        try {
            ImportResult result = priceImportService.dryRunImport(
                    file.getInputStream(),
                    file.getOriginalFilename()
            );

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Dry-run failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ImportResult.dryRun(
                            List.of(),
                            List.of("Error: " + e.getMessage()),
                            0,
                            file.getOriginalFilename()
                    ));
        }
    }

    private boolean isValidExcelFile(MultipartFile file) {
        String contentType = file.getContentType();
        String fileName = file.getOriginalFilename();

        return (contentType != null &&
                (contentType.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") ||
                        contentType.equals("application/vnd.ms-excel"))) ||
                (fileName != null &&
                        (fileName.toLowerCase().endsWith(".xlsx") ||
                                fileName.toLowerCase().endsWith(".xls")));
    }
}
