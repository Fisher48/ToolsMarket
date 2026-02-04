package ru.fisher.ToolsMarket.recaptcha;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.fisher.ToolsMarket.exceptions.CaptchaException;
import ru.fisher.ToolsMarket.util.LoginAttemptService;

import java.io.IOException;

@Component
@Slf4j
@RequiredArgsConstructor
public class RecaptchaValidationFilter extends OncePerRequestFilter {

    private final CaptchaService captchaService;
    private final LoginAttemptService attemptService;
    private final RecaptchaConfig recaptchaConfig;

    @Value("${app.security.login-attempts.max-without-captcha}")
    private int maxWithoutCaptcha;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        String uri = request.getRequestURI();
        String ip = getClientIp(request);
        String method = request.getMethod();

        log.debug("Checking CAPTCHA for: {} {}, IP: {}", method, uri, ip);

        // Проверяем только если CAPTCHA включена
        if (!captchaService.isEnabled()) {
            chain.doFilter(request, response);
            return;
        }

        // 1. Проверка ЛОГИНА
        if (uri.equals("/auth/login") && method.equalsIgnoreCase("POST")) {
            handleLogin(request, response, chain, ip);
            return;
        }

        // 2. Проверка РЕГИСТРАЦИИ
        if (uri.equals("/auth/register") && method.equalsIgnoreCase("POST")) {
            handleRegister(request, response, chain, ip);
            return;
        }

        // Для остальных запросов пропускаем дальше
        chain.doFilter(request, response);
    }

    private void handleLogin(HttpServletRequest request,
                             HttpServletResponse response,
                             FilterChain chain,
                             String ip) throws IOException, ServletException {

        boolean captchaRequired = false;

        // Если настроено "всегда на логине" ИЛИ много неудачных попыток
        if (recaptchaConfig.isAlwaysOnLogin()) {
            captchaRequired = true;
            log.debug("CAPTCHA required for login (always on)");
        } else {
            captchaRequired = attemptService.isCaptchaRequired(ip, maxWithoutCaptcha);
            log.debug("CAPTCHA required for login (based on attempts): {}", captchaRequired);
        }

        if (captchaRequired) {
            validateCaptcha(request, response, chain, ip, recaptchaConfig.getLoginAction());
        } else {
            chain.doFilter(request, response);
        }
    }

    private void handleRegister(HttpServletRequest request,
                                HttpServletResponse response,
                                FilterChain chain,
                                String ip) throws IOException, ServletException {

        // На регистрации всегда проверяем, если включено
        if (recaptchaConfig.isAlwaysOnRegister()) {
            log.debug("CAPTCHA required for register (always on)");
            validateCaptcha(request, response, chain, ip, recaptchaConfig.getRegisterAction());
        } else {
            chain.doFilter(request, response);
        }
    }

    private void validateCaptcha(HttpServletRequest request,
                                 HttpServletResponse response,
                                 FilterChain chain,
                                 String ip,
                                 String action) throws IOException, ServletException {

        String captchaToken = request.getParameter("g-recaptcha-response");

        if (StringUtils.isEmpty(captchaToken)) {
            log.warn("No CAPTCHA token provided for {} from IP: {}", action, ip);
            setErrorAndRedirect(request, response, action);
            return;
        }

        try {
            captchaService.verify(captchaToken, ip, action);
            log.debug("CAPTCHA verification successful for {} from IP: {}", action, ip);
            chain.doFilter(request, response);

        } catch (CaptchaException ex) {
            log.error("CAPTCHA verification failed for {} from IP: {} - {}",
                    action, ip, ex.getMessage());
            setErrorAndRedirect(request, response, action);
        }
    }

    private void setErrorAndRedirect(HttpServletRequest request,
                                     HttpServletResponse response,
                                     String action) throws IOException {

        HttpSession session = request.getSession();
        String errorMessage = switch (action) {
            case "login" -> "Ошибка проверки безопасности при входе. Попробуйте еще раз.";
            case "register" -> "Ошибка проверки безопасности при регистрации. Подтвердите, что вы не робот.";
            default -> "Ошибка проверки безопасности.";
        };

        session.setAttribute("captchaError", errorMessage);

        // Редирект на соответствующую страницу
        String redirectUrl = action.equals("register")
                ? "/auth/register?error=captcha"
                : "/auth/login?error=captcha";

        response.sendRedirect(redirectUrl);
    }

    public String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
