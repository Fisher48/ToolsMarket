package ru.fisher.ToolsMarket.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        HttpSession session = request.getSession();

        // 1. Сначала пробуем взять redirect из сессии (тот, что мы сохранили сами)
        String redirectUrl = (String) session.getAttribute("loginRedirectUrl");
        log.debug("redirect из сессии: {}", redirectUrl);

        // 2. Очищаем сессию
        session.removeAttribute("loginRedirectUrl");

        // 3. Если нет в сессии - берем из параметра формы
        if (redirectUrl == null || redirectUrl.isEmpty()) {
            redirectUrl = request.getParameter("redirect");
            log.debug("redirect из параметра: {}", redirectUrl);
        }

        // 4. Игнорируем API запросы (они не нужны для редиректа)
        if (redirectUrl != null && redirectUrl.contains("/api/")) {
            log.info("API запрос, игнорируем");
            redirectUrl = null;
        }

        // 5. Проверяем, что URL безопасен (только относительные пути)
        if (redirectUrl != null && redirectUrl.startsWith("/") && !redirectUrl.contains("/auth/")) {
            response.sendRedirect(redirectUrl);
        } else {
            response.sendRedirect("/");
        }
    }
}
