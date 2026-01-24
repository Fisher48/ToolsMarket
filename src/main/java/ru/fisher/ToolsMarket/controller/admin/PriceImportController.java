package ru.fisher.ToolsMarket.controller.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import ru.fisher.ToolsMarket.dto.ImportResult;
import ru.fisher.ToolsMarket.service.PriceImportService;

import java.io.IOException;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/prices")
public class PriceImportController {

    private final PriceImportService priceImportService;

    @GetMapping
    public String importPage(Model model) {
        model.addAttribute("result", null);
        return "admin/prices/import";
    }

    @PostMapping("/import")
    public String handleImport(@RequestParam("file") MultipartFile file,
                               Model model) throws IOException {
        if (file.isEmpty()) {
            model.addAttribute("error", "Файл пустой");
            return "admin/prices/import";
        }

        if (!isValidExcelFile(file)) {
            model.addAttribute("error",
                    "Поддерживаются только файлы Excel (.xlsx, .xls)");
            return "admin/prices/import";
        }

        ImportResult result = priceImportService.importPrices(file.getInputStream(), file.getOriginalFilename());
        model.addAttribute("result", result);
        return "admin/prices/import";
    }

    private boolean isValidExcelFile(MultipartFile file) {
        String contentType = file.getContentType();
        String fileName = file.getOriginalFilename();

        return (contentType != null &&
                (contentType.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") ||
                        contentType.equals("application/vnd.ms-excel"))) ||
                (fileName != null &&
                        (fileName.endsWith(".xlsx") || fileName.endsWith(".xls")));
    }
}
