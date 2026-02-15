package ru.fisher.ToolsMarket.controller.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import ru.fisher.ToolsMarket.parsingXml.StemYmlImportService;

import java.util.concurrent.CompletableFuture;

@Controller
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/admin/parser")
public class XmlParserController {

    private final StemYmlImportService ymlImportService;
    private final TaskExecutor taskExecutor;

    @GetMapping
    public String importPage(Model model) {
        model.addAttribute("result", null);
        model.addAttribute("running", false);
        model.addAttribute("defaultUrl",
                "https://stemru.ru/bitrix/catalog_export/export_stemtechno_dealer.xml");
        return "admin/parser/xml_parser";
    }

    @PostMapping("/run")
    public CompletableFuture<String> runImport(
            @RequestParam("xmlUrl") String xmlUrl,
            Model model) {

        log.info("YML Import request: url={}", xmlUrl);

        return CompletableFuture.supplyAsync(() -> {
            try {
                model.addAttribute("running", true);

                StemYmlImportService.ImportResult result =
                        ymlImportService.importFromUrl(xmlUrl);

                model.addAttribute("result", result);
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
