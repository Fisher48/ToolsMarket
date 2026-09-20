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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import ru.fisher.ToolsMarket.dto.ExcelImportResult;
import ru.fisher.ToolsMarket.models.User;
import ru.fisher.ToolsMarket.service.ExcelProductImportService;
import ru.fisher.ToolsMarket.service.UserService;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Controller
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@RequestMapping("/admin/excel-import")
public class ExcelImportController {

    private final ExcelProductImportService excelImportService;
    private final UserService userService;
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
            @AuthenticationPrincipal UserDetails userDetails,
            Model model) {

        log.info("Excel import request: file={}, size={}",
                file.getOriginalFilename(), file.getSize());

        // Резолвим юзера в потоке запроса, а не в воркер-потоке импорта:
        // taskExecutor — простой ThreadPoolTaskExecutor без проброса SecurityContext.
        Long currentUserId = userService.findByUsername(userDetails.getUsername())
                .map(User::getId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        final Long importingUserId = currentUserId;

        return CompletableFuture.supplyAsync(() -> {
            try {
                model.addAttribute("running", true);

                ExcelImportResult result = excelImportService.importFromExcel(file, importingUserId);

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