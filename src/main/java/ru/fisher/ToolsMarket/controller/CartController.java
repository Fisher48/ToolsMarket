package ru.fisher.ToolsMarket.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.fisher.ToolsMarket.dto.CartItemDto;
import ru.fisher.ToolsMarket.models.Cart;
import ru.fisher.ToolsMarket.models.Product;
import ru.fisher.ToolsMarket.models.User;
import ru.fisher.ToolsMarket.service.CartService;
import ru.fisher.ToolsMarket.service.ProductService;
import ru.fisher.ToolsMarket.service.UserService;

import java.math.BigDecimal;
import java.util.List;

@Controller
@RequestMapping("/cart")
@RequiredArgsConstructor
@Slf4j
public class CartController {

    private final CartService cartService;
    private final ProductService productService;
    private final UserService userService;

    @GetMapping
    public String viewCart(Model model) {
        User user = getCurrentUser();
        if (user == null) {
            return "redirect:/auth/login";
        }

        Cart cart = cartService.getOrCreateCart(user.getId());
        List<CartItemDto> items = cartService.getUserCartItems(user.getId());

        // Вычисляем суммы
        BigDecimal totalAmount = cartService.calculateSummary(items);
        BigDecimal totalWithDiscount = items.stream()
                .map(CartItemDto::getTotalPriceWithDiscount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalDiscount = totalAmount.subtract(totalWithDiscount);

        // Общее количество товаров
        int totalItemCount = items.stream()
                .mapToInt(CartItemDto::getQuantity)
                .sum();

        model.addAttribute("cart", cart);
        model.addAttribute("items", items);
        model.addAttribute("totalItemCount", totalItemCount);
        model.addAttribute("totalAmount", totalAmount);
        model.addAttribute("totalWithDiscount", totalWithDiscount);
        model.addAttribute("totalDiscount", totalDiscount);
        model.addAttribute("hasDiscounts", totalDiscount.compareTo(BigDecimal.ZERO) > 0);
        model.addAttribute("userType", user.getUserType());
        model.addAttribute("userTypeDisplay", user.getUserType().getDisplayName());
        model.addAttribute("currentUser", user);

        return "cart/index";
    }

    @PostMapping("/add")
    public String addToCart(@RequestParam Long productId,
                            @RequestParam(defaultValue = "1") Integer quantity,
                            @RequestParam(defaultValue = "cart") String redirectTo,
                            @RequestHeader(value = "Referer", required = false) String referer,
                            RedirectAttributes redirectAttributes) {

        User user = getCurrentUser();
        if (user == null) {
            return "redirect:/auth/login";
        }

        cartService.addProductToUserCart(user.getId(), productId, quantity);

        // Добавляем сообщение об успехе
        if ("product".equals(redirectTo) || (referer != null && referer.contains("/product/"))) {
            redirectAttributes.addFlashAttribute("cartMessage", "Товар добавлен в корзину");
        }

        return determineRedirectUrl(redirectTo, productId, referer);
    }

    @PostMapping("/remove")
    public String removeFromCart(@RequestParam Long productId,
                                 @RequestParam(defaultValue = "cart") String redirectTo,
                                 @RequestHeader(value = "Referer", required = false) String referer,
                                 RedirectAttributes redirectAttributes) {

        User user = getCurrentUser();
        if (user == null) {
            return "redirect:/auth/login";
        }

        cartService.removeProductFromUserCart(user.getId(), productId);

        return determineRedirectUrl(redirectTo, productId, referer);
    }

    /**
     * Уменьшение количества товара
     */
    @PostMapping("/decrease")
    public String decreaseQuantity(@RequestParam Long productId,
                                   @RequestParam(defaultValue = "cart") String redirectTo,
                                   @RequestHeader(value = "Referer", required = false) String referer,
                                   RedirectAttributes redirectAttributes) {

        User user = getCurrentUser();
        if (user == null) {
            return "redirect:/auth/login";
        }

        cartService.decreaseProductInUserCart(user.getId(), productId);

        return determineRedirectUrl(redirectTo, productId, referer);
    }

    /**
     * Очистка корзины
     */
    @PostMapping("/clear")
    public String clearCart() {
        User user = getCurrentUser();
        if (user == null) {
            return "redirect:/auth/login";
        }

        cartService.clearUserCart(user.getId());
        return "redirect:/cart";
    }

    // =========== Вспомогательные методы ===========

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() &&
                !"anonymousUser".equals(authentication.getPrincipal())) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof UserDetails) {
                String username = ((UserDetails) principal).getUsername();
                return userService.findByUsername(username).orElse(null);
            }
        }
        return null;
    }

    // Вспомогательный метод для определения редиректа
    private String determineRedirectUrl(String redirectTo, Long productId, String referer) {
        // 1. Если явно указано "product" - на страницу товара
        if ("product".equals(redirectTo)) {
            String productTitle = productService.findEntityById(productId)
                    .map(Product::getTitle)
                    .orElse(null);

            if (productTitle != null) {
                return "redirect:/product/" + productTitle;
            }
        }

        // 2. Если явно указано "category" - остаемся в категории
        if ("category".equals(redirectTo) && referer != null && referer.contains("/category/")) {
            return "redirect:" + referer; // Остаемся на той же странице категории
        }

        // 3. Если пришли со страницы товара
        if (referer != null && referer.contains("/product/")) {
            return "redirect:" + referer;
        }

        // 4. Если пришли из корзины
        if ("cart".equals(redirectTo) || (referer != null && referer.contains("/cart"))) {
            return "redirect:/cart";
        }

        // 5. По умолчанию - остаемся на текущей странице если это категория
        if (referer != null && referer.contains("/category/")) {
            return "redirect:" + referer;
        }

        // 6. По умолчанию - в корзину
        return "redirect:/cart";
    }

}
