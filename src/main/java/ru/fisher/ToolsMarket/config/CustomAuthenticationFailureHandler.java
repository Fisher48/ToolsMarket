package ru.fisher.ToolsMarket.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import ru.fisher.ToolsMarket.recaptcha.RecaptchaValidationFilter;
import ru.fisher.ToolsMarket.util.LoginAttemptService;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class CustomAuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private final LoginAttemptService loginAttemptService;
    private final RecaptchaValidationFilter recaptchaValidationFilter;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {

        // Получаем IP клиента
        String ip = recaptchaValidationFilter.getClientIp(request);

        // ВАЖНО: увеличиваем счетчик неудачных попыток
        loginAttemptService.loginFailed(ip);

        log.info("Authentication failed for IP: {}, attempts now: {}",
                ip, loginAttemptService.getAttemptsCount(ip));

        // Перенаправляем на страницу логина с параметром ошибки
        getRedirectStrategy().sendRedirect(request, response, "/auth/login?error=true");
    }
}
