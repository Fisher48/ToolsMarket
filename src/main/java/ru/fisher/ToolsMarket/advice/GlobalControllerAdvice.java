package ru.fisher.ToolsMarket.advice;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalControllerAdvice {

    @ModelAttribute
    public void addCurrentUrl(HttpServletRequest request, Model model) {
        String currentUrl = request.getRequestURI();
        String queryString = request.getQueryString();
        if (queryString != null && !queryString.isEmpty()) {
            currentUrl = currentUrl + "?" + queryString;
        }
        model.addAttribute("currentUrl", currentUrl);

        // не создаём новую сессию
        HttpSession session = request.getSession(false);

        // Не сохраняем URL если:
        // - сессии ещё нет (бот/первый заход)
        // - это API, статика, auth, error
        // - User-Agent похож на бота
        if (session == null) {
            return;
        }

        String userAgent = request.getHeader("User-Agent");
        if (userAgent != null && isBot(userAgent)) {
            return;
        }

        if (currentUrl.startsWith("/api/")
                || currentUrl.startsWith("/auth/")
                || currentUrl.startsWith("/login")
                || currentUrl.startsWith("/register")
                || currentUrl.startsWith("/error")
                || currentUrl.startsWith("/actuator/")
                || currentUrl.startsWith("/images/")
                || currentUrl.startsWith("/css/")
                || currentUrl.startsWith("/js/")
                || currentUrl.startsWith("/webjars/")) {
            return;
        }

        session.setAttribute("loginRedirectUrl", currentUrl);
    }

    private boolean isBot(String ua) {
        String lower = ua.toLowerCase();
        return lower.contains("bot") || lower.contains("crawl") || lower.contains("spider")
                || lower.contains("slurp") || lower.contains("scan") || lower.contains("python")
                || lower.contains("java/") || lower.contains("http") || lower.contains("curl")
                || lower.contains("wget") || lower.contains("ahrefs") || lower.contains("semrush");
    }
}
