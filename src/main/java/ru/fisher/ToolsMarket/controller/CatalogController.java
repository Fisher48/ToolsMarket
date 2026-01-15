package ru.fisher.ToolsMarket.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import ru.fisher.ToolsMarket.dto.*;
import ru.fisher.ToolsMarket.models.Cart;
import ru.fisher.ToolsMarket.models.User;
import ru.fisher.ToolsMarket.service.CartService;
import ru.fisher.ToolsMarket.service.CategoryService;
import ru.fisher.ToolsMarket.service.ProductService;
import ru.fisher.ToolsMarket.service.UserService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;


@Controller
@Slf4j
@RequestMapping()
public class CatalogController {

    private final ProductService productService;
    private final CategoryService categoryService;
    private final UserService userService;
    private final CartService cartService;

    public CatalogController(ProductService productService, CategoryService categoryService, UserService userService, CartService cartService) {
        this.productService = productService;
        this.categoryService = categoryService;
        this.userService = userService;
        this.cartService = cartService;
    }

    @ModelAttribute
    public void addCommonAttributes(Model model,
                                    @AuthenticationPrincipal UserDetails userDetails) {
        // Добавляем корневые категории
        List<CategoryDto> rootCategories = categoryService.getRootCategories();
        model.addAttribute("categories", rootCategories);

        // Добавляем информацию о пользователе и скидках
        if (userDetails != null) {
            userService.findByUsername(userDetails.getUsername())
                    .ifPresent(user -> {
                        model.addAttribute("currentUser", user);
                        model.addAttribute("userType", user.getUserType());
                        model.addAttribute("userTypeDisplay", user.getUserType().getDisplayName());
                    });
        }
    }

    @GetMapping("/product/{title}")
    public String product(@PathVariable String title,
                          @AuthenticationPrincipal UserDetails userDetails,
                          @CookieValue(value = "sessionId", required = false) String sessionId,
                          HttpServletResponse response,
                          Model model) {

        // Получаем пользователя
        User user = null;
        if (userDetails != null) {
            user = userService.findByUsername(userDetails.getUsername()).orElse(null);
        }

        ProductDto product = productService.findByTitleWithDiscounts(title, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Товар не найден"));

        // === ДОБАВЛЕНО: Проверка товара в корзине ===
        boolean isInCart = false;
        int cartQuantity = 0;

        try {
            // Получаем sessionId или создаем новый
            if (sessionId == null || sessionId.isEmpty()) {
                sessionId = UUID.randomUUID().toString();
                Cookie cookie = new Cookie("sessionId", sessionId);
                cookie.setPath("/");
                response.addCookie(cookie);
            }

            // Получаем ID пользователя
            Long userId = user != null ? user.getId() : null;

            // Используем CartService для проверки (нужно будет инжектить)
            Cart cart = cartService.getOrCreateCart(userId, sessionId);
            isInCart = cartService.isProductInCart(cart.getId(), product.getId());

            if (isInCart) {
                cartQuantity = cartService.getProductQuantityInCart(cart.getId(), product.getId());
            }

        } catch (Exception e) {
            // Логируем ошибку, но не прерываем работу
            log.warn("Ошибка при проверке товара в корзине: {}", e.getMessage());
        }

        // Добавляем сообщения из flash attributes
        if (model.containsAttribute("cartMessage")) {
            model.addAttribute("showCartMessage", true);
        }

        model.addAttribute("product", product);
        model.addAttribute("isInCart", isInCart);
        model.addAttribute("cartQuantity", cartQuantity);
        return "catalog/product";
    }

    @GetMapping("/search")
    public String search(@RequestParam(required = false) String q,
                         @RequestParam(defaultValue = "0") int page,
                         @AuthenticationPrincipal UserDetails userDetails,
                         Model model) {

        User user = null;
        if (userDetails != null) {
            user = userService.findByUsername(userDetails.getUsername()).orElse(null);
        }

        Page<ProductListDto> searchResults;

        if (q == null || q.trim().isEmpty()) {
            searchResults = Page.empty();
        } else {
            searchResults = productService.searchWithDiscounts(q.trim(), user, PageRequest.of(page, 12));
        }

        model.addAttribute("query", q);
        model.addAttribute("results", searchResults);
        model.addAttribute("resultsCount", searchResults.getTotalElements());

        return "catalog/search";
    }


    @GetMapping("/category/{title}")
    public String category(@PathVariable String title,
                           @RequestParam(defaultValue = "0") int page,
                           @AuthenticationPrincipal UserDetails userDetails,
                           @CookieValue(value = "sessionId", required = false) String sessionId,
                           HttpServletResponse response,
                           Model model) {

        User user = null;
        if (userDetails != null) {
            user = userService.findByUsername(userDetails.getUsername()).orElse(null);
        }

        CategoryDto category = categoryService.findByTitle(title)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        Page<ProductListDto> products = productService.findByCategoryWithDiscounts(
                category.getId(), user, PageRequest.of(page, 12));

        // === ДОБАВЛЕНО: Проверка товаров в корзине ===
        Map<Long, Integer> cartProductQuantities = new HashMap<>();

        try {
            // Получаем sessionId или создаем новый
            if (sessionId == null || sessionId.isEmpty()) {
                sessionId = UUID.randomUUID().toString();
                Cookie cookie = new Cookie("sessionId", sessionId);
                cookie.setPath("/");
                response.addCookie(cookie);
            }

            // Получаем ID пользователя
            Long userId = user != null ? user.getId() : null;

            // Получаем корзину
            Cart cart = cartService.getOrCreateCart(userId, sessionId);

            // Получаем все товары в корзине
            List<CartItemDto> cartItems = cartService.getCartItems(cart.getId());

            // Создаем мапу productId -> quantity
            for (CartItemDto cartItem : cartItems) {
                if (cartItem.getProductId() != null) {
                    cartProductQuantities.put(cartItem.getProductId(), cartItem.getQuantity());
                }
            }

        } catch (Exception e) {
            log.warn("Ошибка при проверке корзины: {}", e.getMessage());
        }

        // === ДОБАВЛЕНО: Добавляем информацию о товарах в корзине в каждый продукт ===
        List<ProductListDto> productsWithCartInfo = products.getContent().stream()
                .map(product -> {
                    // Создаем копию продукта с информацией о корзине
                    ProductListDto enhancedProduct = new ProductListDto();

                    // Копируем все поля из оригинального продукта
                    BeanUtils.copyProperties(product, enhancedProduct);

                    // Добавляем информацию о корзине
                    Integer cartQuantity = cartProductQuantities.get(product.getId());
                    if (cartQuantity != null && cartQuantity > 0) {
                        enhancedProduct.setInCart(true);
                        enhancedProduct.setCartQuantity(cartQuantity);
                    } else {
                        enhancedProduct.setInCart(false);
                        enhancedProduct.setCartQuantity(0);
                    }

                    return enhancedProduct;
                })
                .toList();

        // Создаем новую страницу с обновленными продуктами
        Page<ProductListDto> enhancedProducts = new PageImpl<>(
                productsWithCartInfo,
                products.getPageable(),
                products.getTotalElements()
        );

        model.addAttribute("category", category);
        model.addAttribute("products", enhancedProducts);
        model.addAttribute("cartProductQuantities", cartProductQuantities); // Можно добавить для шаблона

        return "catalog/category";
    }


//    @GetMapping("/product/{title}")
//    public String product(@PathVariable String title, Model model) {
//        ProductDto product = productService.findWithAttributesByTitle(title)
//                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Товар не найден"));
//
//        model.addAttribute("product", product);
//        return "catalog/product";
//    }
}
