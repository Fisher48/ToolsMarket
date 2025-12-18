package ru.fisher.ToolsMarket.controller;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import ru.fisher.ToolsMarket.dto.UserDto;
import ru.fisher.ToolsMarket.models.User;
import ru.fisher.ToolsMarket.service.CartService;
import ru.fisher.ToolsMarket.service.UserService;


@Controller
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final UserService userService;
    private final CartService cartService;

    @GetMapping("/login")
    public String loginPage(HttpSession session, Model model) {
        // Проверяем, есть ли анонимная корзина для объединения
        if (session.getAttribute("hasAnonymousCart") != null) {
            model.addAttribute("hasAnonymousCart", true);
        }
        return "auth/login";
    }

    @GetMapping("/register")
    public String registerPage(Model model, HttpSession session) {
        model.addAttribute("userDto", new UserDto());

        // Проверяем, есть ли анонимная корзина для объединения
        if (session.getAttribute("hasAnonymousCart") != null) {
            model.addAttribute("hasAnonymousCart", true);
        }

        return "auth/register";
    }

    @PostMapping("/register")
    public String register(@ModelAttribute("userDto") @Valid UserDto userDto,
                           BindingResult bindingResult,
                           Model model,
                           HttpSession session,
                           @CookieValue(value = "sessionId", required = false) String sessionId) {

        if (bindingResult.hasErrors()) {
            return "auth/register";
        }

        if (!userDto.getPassword().equals(userDto.getConfirmPassword())) {
            model.addAttribute("passwordError", "Пароли не совпадают");
            return "auth/register";
        }

        try {
            User user = userService.registerUser(
                    userDto.getUsername(),
                    userDto.getEmail(),
                    userDto.getPassword(),
                    userDto.getFirstName(),
                    userDto.getLastName()
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

            model.addAttribute("successMessage",
                    "Регистрация успешна! Теперь вы можете войти в систему.");

            return "redirect:/auth/login?success";

        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "auth/register";
        }
    }

    @GetMapping("/access-denied")
    public String accessDenied() {
        return "error/403";
    }

}
