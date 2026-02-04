package ru.fisher.ToolsMarket.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import ru.fisher.ToolsMarket.dto.UserDto;
import ru.fisher.ToolsMarket.models.User;
import ru.fisher.ToolsMarket.recaptcha.CaptchaService;
import ru.fisher.ToolsMarket.recaptcha.RecaptchaConfig;

import ru.fisher.ToolsMarket.recaptcha.RecaptchaValidationFilter;
import ru.fisher.ToolsMarket.service.CartService;
import ru.fisher.ToolsMarket.service.UserService;
import ru.fisher.ToolsMarket.util.LoginAttemptService;



@Controller
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final UserService userService;
    private final CartService cartService;
    private final CaptchaService captchaService;
    private final RecaptchaConfig recaptchaConfig;
    private final RecaptchaValidationFilter recaptchaValidationFilter;
    private final LoginAttemptService loginAttemptService;

    @Value("${app.security.login-attempts.max-without-captcha}")
    private int maxWithoutCaptcha;

    @GetMapping("/login")
    public String loginPage(@RequestParam(value = "success", required = false) String success,
                            @RequestParam(value = "error", required = false) String error,
                            @RequestParam(value = "logout", required = false) String logout,
                            HttpServletRequest request,
                            HttpSession session,
                            Model model) {

        String clientIp = recaptchaValidationFilter.getClientIp(request);

        // Получаем количество попыток и проверяем нужна ли капча
        int attempts = loginAttemptService.getAttemptsCount(clientIp);
        boolean captchaRequired = loginAttemptService.isCaptchaRequired(clientIp, maxWithoutCaptcha);

        // Отладочная информация
        log.debug("=== LOGIN PAGE LOADED ===");
        log.debug("Client IP: {}", clientIp);
        log.debug("Login attempts: {}", attempts);
        log.debug("Captcha required: {}", captchaRequired);

        // Добавляем в модель
        model.addAttribute("loginAttempts", attempts);
        model.addAttribute("captchaRequired", captchaRequired);

        // Если была ошибка капчи из сессии
        String captchaError = (String) session.getAttribute("captchaError");
        if (captchaError != null) {
            model.addAttribute("captchaError", captchaError);
            session.removeAttribute("captchaError");
            log.debug("Captcha error from session: {}", captchaError);
        }

        // Если была ошибка капчи из параметра
        if (error != null && error.equals("captcha")) {
            model.addAttribute("captchaError",
                    "Ошибка проверки безопасности. Попробуйте еще раз.");
        }

        // Обычная ошибка аутентификации
        if (error != null && error.equals("true")) {
            log.debug("Authentication error from Spring Security");
        }

        // Сообщение об успешной регистрации
        if (success != null) {
            model.addAttribute("registrationSuccess",
                    "Регистрация успешно завершена! Теперь вы можете войти в систему.");
        }

        // Добавляем ключ reCAPTCHA
        model.addAttribute("recaptchaSiteKey", captchaService.getSiteKey());
        log.debug("reCAPTCHA site key: {}", captchaService.getSiteKey());
        model.addAttribute("recaptchaVersion", captchaService.getVersion());
        model.addAttribute("recaptchaEnabled", captchaService.isEnabled());
        model.addAttribute("isRecaptchaV2", captchaService.isV2());
        model.addAttribute("isRecaptchaV3", captchaService.isV3());
        model.addAttribute("recaptchaScriptUrl", captchaService.getScriptUrl());

        return "auth/login";
    }

    @GetMapping("/register")
    public String registerPage(Model model, HttpSession session) {
        model.addAttribute("userDto", new UserDto());

        // Добавляем информацию о CAPTCHA
        model.addAttribute("recaptchaSiteKey", captchaService.getSiteKey());
        model.addAttribute("recaptchaVersion", captchaService.getVersion());
        model.addAttribute("recaptchaEnabled", captchaService.isEnabled());
        model.addAttribute("isRecaptchaV2", captchaService.isV2());
        model.addAttribute("isRecaptchaV3", captchaService.isV3());
        model.addAttribute("recaptchaScriptUrl", captchaService.getScriptUrl());

        // Всегда показываем CAPTCHA на регистрации, если включено
        model.addAttribute("captchaRequired",
                recaptchaConfig.isEnabled() && recaptchaConfig.isAlwaysOnRegister());

        // Если была ошибка капчи
        String captchaError = (String) session.getAttribute("captchaError");
        if (captchaError != null) {
            model.addAttribute("captchaError", captchaError);
            session.removeAttribute("captchaError");
        }

        return "auth/register";
    }

    @PostMapping("/register")
    public String register(@ModelAttribute("userDto") @Valid UserDto userDto,
                           BindingResult bindingResult,
                           Model model,
                           HttpSession session,
                           @CookieValue(value = "sessionId", required = false) String sessionId) {

        // Проверка CAPTCHA уже выполнена в фильтре

        if (bindingResult.hasErrors()) {
            // При ошибках валидации возвращаем снова на страницу регистрации
            // Нужно добавить атрибуты CAPTCHA
            addCaptchaAttributes(model);
            return "auth/register";
        }

        if (!userDto.getPassword().equals(userDto.getConfirmPassword())) {
            model.addAttribute("passwordError", "Пароли не совпадают");
            addCaptchaAttributes(model);
            return "auth/register";
        }

        try {
            User user = userService.registerUser(
                    userDto.getUsername(),
                    userDto.getEmail(),
                    userDto.getPassword(),
                    userDto.getFirstName(),
                    userDto.getLastName(),
                    userDto.getPhone()
            );

            log.info("Пользователь зарегистрирован: {}", user.getUsername());

            // Если была анонимная корзина - объединяем
            if (StringUtils.hasText(sessionId)) {
                try {
                    cartService.mergeCartToUser(sessionId, user.getId());
                    session.removeAttribute("hasAnonymousCart");
                    session.removeAttribute("anonymousSessionId");
                    model.addAttribute("cartMerged", true);
                } catch (Exception e) {
                    log.warn("Не удалось объединить корзину: {}", e.getMessage());
                }
            }

            return "redirect:/auth/login?success";

        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            addCaptchaAttributes(model);
            return "auth/register";
        }
    }

    private void addCaptchaAttributes(Model model) {
        model.addAttribute("recaptchaSiteKey", captchaService.getSiteKey());
        model.addAttribute("recaptchaVersion", captchaService.getVersion());
        model.addAttribute("recaptchaEnabled", captchaService.isEnabled());
        model.addAttribute("isRecaptchaV2", captchaService.isV2());
        model.addAttribute("isRecaptchaV3", captchaService.isV3());
        model.addAttribute("recaptchaScriptUrl", captchaService.getScriptUrl());
        model.addAttribute("captchaRequired",
                recaptchaConfig.isEnabled() && recaptchaConfig.isAlwaysOnRegister());
    }

    @GetMapping("/access-denied")
    public String accessDenied() {
        return "error/403";
    }
}
