package ru.fisher.ToolsMarket.controller.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.fisher.ToolsMarket.dto.ExcelImportResult;
import ru.fisher.ToolsMarket.service.ExcelProductImportService;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/excel-import")
public class ExcelImportController {

    private final ExcelProductImportService excelImportService;
    private final TaskExecutor taskExecutor;

    @GetMapping
    public String importPage(Model model) {
        model.addAttribute("result", null);
        model.addAttribute("running", false);
        return "admin/excel/import";
    }

    @PostMapping("/import")
    public CompletableFuture<String> handleImport(
            @RequestParam("file") MultipartFile file,
            Model model) {

        log.info("Excel import request: file={}, size={}",
                file.getOriginalFilename(), file.getSize());

        return CompletableFuture.supplyAsync(() -> {
            try {
                model.addAttribute("running", true);

                ExcelImportResult result = excelImportService.importFromExcel(file);

                model.addAttribute("result", result);
                model.addAttribute("running", false);

                return "admin/excel/import";

            } catch (Exception e) {
                log.error("Error importing Excel", e);
                model.addAttribute("error", "Ошибка импорта: " + e.getMessage());
                model.addAttribute("running", false);
                return "admin/excel/import";
            }
        }, taskExecutor);
    }
}