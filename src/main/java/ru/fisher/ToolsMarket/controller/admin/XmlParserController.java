package ru.fisher.ToolsMarket.controller.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import ru.fisher.ToolsMarket.parsingXml.StemYmlImportService;

import java.util.concurrent.CompletableFuture;

@Controller
@Slf4j
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/admin/parser")
public class XmlParserController {

    private final StemYmlImportService ymlImportService;
    private final TaskExecutor taskExecutor;

    @GetMapping
    public String importPage(Model model) {
        model.addAttribute("result", null);
        model.addAttribute("dryRun", false);
        model.addAttribute("running", false);
        model.addAttribute("defaultUrl",
                "https://stemru.ru/bitrix/catalog_export/export_stemtechno_dealer.xml");
        return "admin/parser/xml_parser";
    }

    @PostMapping("/run")
    public CompletableFuture<String> runImport(
            @RequestParam("xmlUrl") String xmlUrl,
            @RequestParam(value = "dryRun", defaultValue = "false") boolean dryRun,
            Model model) {

        log.info("YML {} request: url={}", dryRun ? "предпросмотр (dry-run)" : "import", xmlUrl);

        return CompletableFuture.supplyAsync(() -> {
            try {
                model.addAttribute("running", true);

                StemYmlImportService.ImportResult result =
                        dryRun ? ymlImportService.previewFromUrl(xmlUrl)
                               : ymlImportService.importFromUrl(xmlUrl);

                model.addAttribute("result", result);
                model.addAttribute("dryRun", dryRun);
                model.addAttribute("xmlUrl", xmlUrl);
                model.addAttribute("running", false);

                return "admin/parser/xml_parser";

            } catch (Exception e) {
                log.error("Error importing YML", e);
                model.addAttribute("error", "Ошибка импорта: " + e.getMessage());
                model.addAttribute("xmlUrl", xmlUrl);
                model.addAttribute("running", false);
                return "admin/parser/xml_parser";
            }
        }, taskExecutor);
    }
}
