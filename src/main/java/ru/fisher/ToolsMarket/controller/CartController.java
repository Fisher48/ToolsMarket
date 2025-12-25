package ru.fisher.ToolsMarket.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.fisher.ToolsMarket.dto.CartItemDto;
import ru.fisher.ToolsMarket.models.Cart;
import ru.fisher.ToolsMarket.models.User;
import ru.fisher.ToolsMarket.service.CartService;
import ru.fisher.ToolsMarket.service.ProductService;
import ru.fisher.ToolsMarket.service.UserService;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/cart")
@RequiredArgsConstructor
@Slf4j
public class CartController {

    private final CartService cartService;
    private final ProductService productService;
    private final UserService userService; // Добавляем UserService

    @GetMapping
    public String viewCart(@CookieValue(value = "sessionId", required = false) String sessionId,
                           HttpServletResponse response, HttpSession session,
                           Model model, Authentication authentication) {

        String finalSessionId = getOrCreateSessionId(sessionId, response);
        Long userId = getCurrentUserId(authentication);

        // Получаем корзину с учетом пользователя
        Cart cart = cartService.getOrCreateCart(userId, finalSessionId);
        List<CartItemDto> items = cartService.getCartItems(cart.getId());

        // Логирование
        log.debug("Cart items with titles:");
        items.forEach(item ->
                log.debug("Product: '{}', Title (URL): '{}'",
                        item.getProductName(), item.getProductTitle())
        );

        // Обновляем счетчик в сессии
        updateCartItemCountInSession(session, cart);

        // Вычисляем общую сумму
        BigDecimal totalAmount = items.stream()
                .map(CartItemDto::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Если пользователь залогинен, проверяем нужно ли объединить корзины
        if (userId != null && StringUtils.hasText(finalSessionId)) {
            session.setAttribute("hasAnonymousCart", true);
            session.setAttribute("anonymousSessionId", finalSessionId);
        }

        model.addAttribute("cart", cart);
        model.addAttribute("items", items);
        model.addAttribute("totalAmount", totalAmount);
        model.addAttribute("sessionId", finalSessionId);
        model.addAttribute("isAuthenticated", userId != null);

        return "cart/index";
    }

    @PostMapping("/add")
    public String addToCart(@RequestParam Long productId,
                            @RequestParam(defaultValue = "1") Integer quantity,
                            @CookieValue(value = "sessionId", required = false) String sessionId,
                            HttpServletResponse response, HttpSession session,
                            Authentication authentication) {

        String finalSessionId = getOrCreateSessionId(sessionId, response);
        Long userId = getCurrentUserId(authentication);

        Cart cart = cartService.getOrCreateCart(userId, finalSessionId);
        cartService.addProductWithQuantity(cart.getId(), productId, quantity);

        // Обновляем счетчик в сессии
        updateCartItemCountInSession(session, cart);

        return "redirect:/cart";
    }

    @PostMapping("/remove")
    public String removeFromCart(@RequestParam Long productId,
                                 @CookieValue("sessionId") String sessionId,
                                 HttpSession session,
                                 Authentication authentication) {

        Long userId = getCurrentUserId(authentication);
        Cart cart = cartService.getOrCreateCart(userId, sessionId);
        cartService.removeProduct(cart.getId(), productId);

        // Обновляем счетчик в сессии
        updateCartItemCountInSession(session, cart);

        return "redirect:/cart";
    }

    @PostMapping("/decrease")
    public String decreaseQuantity(@RequestParam Long productId,
                                   @CookieValue("sessionId") String sessionId,
                                   HttpSession session,
                                   Authentication authentication) {

        Long userId = getCurrentUserId(authentication);
        Cart cart = cartService.getOrCreateCart(userId, sessionId);
        cartService.decreaseProductQuantity(cart.getId(), productId);

        // Обновляем счетчик в сессии
        updateCartItemCountInSession(session, cart);

        return "redirect:/cart";
    }

    /**
     * Объединение анонимной корзины с пользовательской после логина
     */
    @PostMapping("/merge")
    public String mergeCart(@CookieValue("sessionId") String sessionId,
                            Authentication authentication,
                            RedirectAttributes redirectAttributes) {

        Long userId = getCurrentUserId(authentication);
        if (userId == null) {
            redirectAttributes.addFlashAttribute("error", "Пользователь не авторизован");
            return "redirect:/cart";
        }

        try {
            cartService.mergeCartToUser(sessionId, userId);
            redirectAttributes.addFlashAttribute("success",
                    "Корзина успешно объединена с вашей учетной записью");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "Ошибка при объединении корзины: " + e.getMessage());
        }

        return "redirect:/cart";
    }

    /**
     * Очистка корзины
     */
    @PostMapping("/clear")
    public String clearCart(@CookieValue("sessionId") String sessionId,
                            Authentication authentication,
                            HttpSession session) {

        Long userId = getCurrentUserId(authentication);
        Cart cart = cartService.getOrCreateCart(userId, sessionId);

        // Используем новый метод
        cartService.clearCart(cart.getId());

        session.setAttribute("cartItemCount", 0);
        return "redirect:/cart";
    }

    // =========== Вспомогательные методы ===========

    private String getOrCreateSessionId(String sessionId, HttpServletResponse response) {
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = UUID.randomUUID().toString();
            Cookie cookie = new Cookie("sessionId", sessionId);
            cookie.setPath("/");
            response.addCookie(cookie);
        }
        return sessionId;
    }

    private Long getCurrentUserId(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof UserDetails) {
                String username = ((UserDetails) principal).getUsername();
                return userService.findByUsername(username)
                        .map(User::getId)
                        .orElse(null);
            }
        }
        return null;
    }

    private void updateCartItemCountInSession(HttpSession session, Cart cart) {
        List<CartItemDto> items = cartService.getCartItems(cart.getId());
        int totalItems = items.stream()
                .mapToInt(CartItemDto::getQuantity)
                .sum();
        session.setAttribute("cartItemCount", totalItems);
    }
}
