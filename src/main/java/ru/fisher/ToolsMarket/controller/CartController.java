package ru.fisher.ToolsMarket.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import ru.fisher.ToolsMarket.dto.CartItemDto;
import ru.fisher.ToolsMarket.models.Cart;
import ru.fisher.ToolsMarket.service.CartService;
import ru.fisher.ToolsMarket.service.ProductService;

import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;
    private final ProductService productService;

    @GetMapping
    public String viewCart(@CookieValue(value = "sessionId", required = false) String sessionId,
                           HttpServletResponse response, HttpSession session,
                           Model model) {
        // Если sessionId нет в куках - создаем новый
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = UUID.randomUUID().toString();
            Cookie cookie = new Cookie("sessionId", sessionId);
            cookie.setPath("/");
            response.addCookie(cookie);
        }

        Cart cart = cartService.getOrCreateCart(null, sessionId);
        List<CartItemDto> items = cartService.getCartItems(cart.getId());

        // Обновляем счетчик в сессии
        updateCartItemCountInSession(session, cart);

        model.addAttribute("cart", cart);
        model.addAttribute("items", items);
        model.addAttribute("sessionId", sessionId);

        return "cart/index";
    }

    @PostMapping("/add")
    public String addToCart(@RequestParam Long productId,
                            @RequestParam(defaultValue = "1") Integer quantity,
                            @CookieValue(value = "sessionId", required = false) String sessionId,
                            HttpServletResponse response, HttpSession session) {
        // Создаем sessionId если нет
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = UUID.randomUUID().toString();
            Cookie cookie = new Cookie("sessionId", sessionId);
            cookie.setPath("/");
            response.addCookie(cookie);
        }

        Cart cart = cartService.getOrCreateCart(null, sessionId);
        cartService.addProductWithQuantity(cart.getId(), productId, quantity);

        // Обновляем счетчик в сессии
        updateCartItemCountInSession(session, cart);

        return "redirect:/cart";
    }

    @PostMapping("/remove")
    public String removeFromCart(@RequestParam Long productId,
                                 @CookieValue("sessionId") String sessionId, HttpSession session) {
        Cart cart = cartService.getOrCreateCart(null, sessionId);
        cartService.removeProduct(cart.getId(), productId);

        // Обновляем счетчик в сессии
        updateCartItemCountInSession(session, cart);

        return "redirect:/cart";
    }

    @PostMapping("/decrease")
    public String decreaseQuantity(@RequestParam Long productId,
                                   @CookieValue("sessionId") String sessionId, HttpSession session) {
        Cart cart = cartService.getOrCreateCart(null, sessionId);
        cartService.decreaseProductQuantity(cart.getId(), productId);

        // Обновляем счетчик в сессии
        updateCartItemCountInSession(session, cart);

        return "redirect:/cart";
    }

    private void updateCartItemCountInSession(HttpSession session, Cart cart) {
        List<CartItemDto> items = cartService.getCartItems(cart.getId());
        int totalItems = items.stream()
                .mapToInt(CartItemDto::getQuantity)
                .sum();
        session.setAttribute("cartItemCount", totalItems);
    }
}
