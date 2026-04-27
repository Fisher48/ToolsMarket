package ru.fisher.ToolsMarket.advice;

import jakarta.servlet.http.HttpServletRequest;
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

        // Сохраняем в сессию для случаев, когда SavedRequest не сработает
        if (!currentUrl.contains("/auth") && !currentUrl.contains("/login") &&
                !currentUrl.contains("/register") && !currentUrl.contains("/error")) {
            request.getSession().setAttribute("loginRedirectUrl", currentUrl);
        }
    }
}
