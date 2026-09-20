package ru.fisher.ToolsMarket.controller.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import ru.fisher.ToolsMarket.models.User;
import ru.fisher.ToolsMarket.parsingXml.StemYmlImportService;
import ru.fisher.ToolsMarket.service.UserService;

import java.util.concurrent.CompletableFuture;

@Controller
@Slf4j
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/admin/parser")
public class XmlParserController {

    private final StemYmlImportService ymlImportService;
    private final UserService userService;
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
            @AuthenticationPrincipal UserDetails userDetails,
            Model model) {

        log.info("YML {} request: url={}", dryRun ? "предпросмотр (dry-run)" : "import", xmlUrl);

        // Резолвим юзера в потоке запроса, а не в воркер-потоке импорта:
        // taskExecutor — простой ThreadPoolTaskExecutor без проброса SecurityContext.
        Long currentUserId = userService.findByUsername(userDetails.getUsername())
                .map(User::getId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        final Long importingUserId = currentUserId;

        return CompletableFuture.supplyAsync(() -> {
            try {
                model.addAttribute("running", true);

                StemYmlImportService.ImportResult result =
                        dryRun ? ymlImportService.previewFromUrl(xmlUrl, importingUserId)
                                : ymlImportService.importFromUrl(xmlUrl, importingUserId);

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
