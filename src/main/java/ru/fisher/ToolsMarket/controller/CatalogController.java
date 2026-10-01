package ru.fisher.ToolsMarket.controller;

import jakarta.validation.constraints.Min;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import ru.fisher.ToolsMarket.dto.CartDTO.CartItemDto;
import ru.fisher.ToolsMarket.dto.CategoryDTO.CategoryDto;
import ru.fisher.ToolsMarket.dto.CategoryDTO.CategoryPageData;
import ru.fisher.ToolsMarket.dto.ProductDTO.ProductDto;
import ru.fisher.ToolsMarket.dto.ProductDTO.ProductListDto;
import ru.fisher.ToolsMarket.models.Cart;
import ru.fisher.ToolsMarket.models.User;
import ru.fisher.ToolsMarket.service.CartService;
import ru.fisher.ToolsMarket.service.CategoryService;
import ru.fisher.ToolsMarket.service.CurrentUserService;
import ru.fisher.ToolsMarket.service.ProductService;
import ru.fisher.ToolsMarket.util.PathSegmentEncoder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Controller
@Slf4j
@RequestMapping()
public class CatalogController {

    private final ProductService productService;
    private final CategoryService categoryService;
    private final CurrentUserService currentUserService;
    private final CartService cartService;
    private final PathSegmentEncoder pathSegmentEncoder;

    public CatalogController(ProductService productService, CategoryService categoryService,
                             CurrentUserService currentUserService, CartService cartService,
                             PathSegmentEncoder pathSegmentEncoder) {
        this.productService = productService;
        this.categoryService = categoryService;
        this.currentUserService = currentUserService;
        this.cartService = cartService;
        this.pathSegmentEncoder = pathSegmentEncoder;
    }

    @ModelAttribute
    public void addCommonAttributes(Model model,
                                    @AuthenticationPrincipal UserDetails userDetails) {
        // Добавляем корневые категории
        List<CategoryDto> rootCategories = categoryService.getRootCategories();
        model.addAttribute("categories", rootCategories);

        // Флаг авторизации
        boolean isAuthenticated = userDetails != null;
        model.addAttribute("isAuthenticated", isAuthenticated);

        // Добавляем информацию о пользователе и скидках
        if (userDetails != null) {
            currentUserService.findCurrentUser(userDetails)
                    .ifPresent(user -> {
                        model.addAttribute("currentUser", user);
                        model.addAttribute("userType", user.getUserType());
                        model.addAttribute("userTypeDisplay", user.getUserType().getDisplayName());
                    });
        }
    }

    /**
     * Страница товара
     */
    @GetMapping("/product/{title}")
    public Object product(@PathVariable String title,
                          @AuthenticationPrincipal UserDetails userDetails,
                          Model model) {

        User user = null;
        if (userDetails != null) {
            user = currentUserService.findCurrentUser(userDetails).orElse(null);
        }

        ProductDto product = productService.findByTitleWithDiscounts(title, user).orElse(null);

        if (product == null) {
            // Клиент обрезал URL на границе ASCII/не-ASCII (кириллический SKU отброшен).
            // По префиксу ровно один товар — редиректим на канонический URL.
            String canonical = productService.findCanonicalTitleForTruncatedUrl(title);
            if (canonical != null) {
                return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY)
                        .header(HttpHeaders.LOCATION,
                                "/product/" + pathSegmentEncoder.encode(canonical))
                        .build();
            }
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Товар не найден");
        }

        // --- Увеличиваем счетчик просмотров ---
        productService.incrementProductViews(title);

        // Проверка товара в корзине
        boolean isInCart = false;
        int cartQuantity = 0;

        if (user != null) {
            try {
                Cart cart = cartService.getOrCreateCart(user.getId());
                isInCart = cartService.isProductInCart(cart.getId(), product.getId());
                if (isInCart) {
                    cartQuantity = cartService.getProductQuantityInCart(cart.getId(), product.getId());
                }
            } catch (Exception e) {
                log.warn("Ошибка при проверке товара в корзине: {}", e.getMessage());
            }
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

    /**
     * Поиск товаров
     */
    @GetMapping("/search")
    public String search(@RequestParam(required = false) String q,
                         @RequestParam(defaultValue = "0") int page,
                         @RequestParam(defaultValue = "name_asc") String sort,
                         @AuthenticationPrincipal UserDetails userDetails,
                         Model model) {

        User user = null;
        if (userDetails != null) {
            user = currentUserService.findCurrentUser(userDetails).orElse(null);
        }

        Page<ProductListDto> searchResults;

        if (q == null || q.trim().isEmpty()) {
            searchResults = Page.empty();
        } else {
            // Создаем PageRequest с сортировкой
            PageRequest pageRequest = PageRequest.of(Math.max(page, 0), 12, productService.getSort(sort));
            searchResults = productService.searchWithDiscounts(q.trim(), user, pageRequest);
        }

        // «Карта количества товаров в корзине» — best-effort lookup для каталога:
        // заполняется только у авторизованных, при ошибке остаётся пустой, каталог не падает
        Map<Long, Integer> cartProductQuantities = new HashMap<>();

        if (user != null) {
            try {
                Cart cart = cartService.getOrCreateCart(user.getId());
                List<CartItemDto> cartItems = cartService.getCartItems(cart.getId());

                for (CartItemDto cartItem : cartItems) {
                    if (cartItem.getProductId() != null) {
                        cartProductQuantities.put(cartItem.getProductId(), cartItem.getQuantity());
                    }
                }
            } catch (Exception e) {
                log.warn("Ошибка при проверке корзины: {}", e.getMessage());
            }
        }

        // «Обогащение карточек состоянием корзины» — пересборка содержимого страницы
        // (inCart/cartQuantity) поверх исходной пагинации, чтобы сохранить страницы и total
        List<ProductListDto> productsWithCartInfo = searchResults.getContent().stream()
                .map(product -> {
                    ProductListDto enhancedProduct = new ProductListDto();
                    BeanUtils.copyProperties(product, enhancedProduct);

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

        Page<ProductListDto> enhancedProducts = new PageImpl<>(
                productsWithCartInfo,
                searchResults.getPageable(),
                searchResults.getTotalElements()
        );

        // Защита: если page больше максимума — редирект на последнюю
        int totalPages = searchResults.getTotalPages();
        if (totalPages > 0 && page >= totalPages) {
            return "redirect:/search?q=" + encode(q)
                    + "&page=" + (totalPages - 1)
                    + "&sort=" + sort;
        }

        model.addAttribute("currentUser", user);
        model.addAttribute("query", q);
        model.addAttribute("results", enhancedProducts);
        model.addAttribute("resultsCount", searchResults.getTotalElements());
        model.addAttribute("cartProductQuantities", cartProductQuantities);
        model.addAttribute("currentSort", sort); // Передаем текущую сортировку в модель для UI

        return "catalog/search";
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }


    @GetMapping("/category/{title}")
    public String category(@PathVariable String title,
                           @RequestParam(defaultValue = "0") int page,
                           @RequestParam(defaultValue = "name_asc") String sort,
                           @AuthenticationPrincipal UserDetails userDetails,
                           Model model) {

        long start = System.currentTimeMillis();

        Long userId = null;
        if (userDetails != null) {
            userId = currentUserService.currentUserId(userDetails);
        }

        CategoryPageData pageData = categoryService.getCategoryPage(title, userId, sort, Math.max(page, 0), 12);

        // Защита: если page больше максимума — редирект на последнюю
        int totalPages = pageData.getProducts().getTotalPages();
        if (totalPages > 0 && page >= totalPages) {
            return "redirect:/category/" + encode(title)
                    + "?page=" + (totalPages - 1)
                    + "&sort=" + sort;
        }

        model.addAttribute("category", pageData.getCategory());
        model.addAttribute("products", pageData.getProducts());
        model.addAttribute("cartProductQuantities", pageData.getCartProductQuantities());
        model.addAttribute("totalElements", pageData.getTotalElements());
        model.addAttribute("currentSort", sort);

        log.debug("Страница Товаров в категории #{} загружена: {} мс",
                title, System.currentTimeMillis() - start);
        return "catalog/category";
    }

    /**
     * Страница категории
     */
//    @GetMapping("/category/{title}")
//    public String category(@PathVariable String title,
//                           @RequestParam(defaultValue = "0") int page,
//                           @RequestParam(defaultValue = "name_asc") String sort,
//                           @AuthenticationPrincipal UserDetails userDetails,
//                           Model model) {
//        long start = System.currentTimeMillis();
//        User user = null;
//        if (userDetails != null) {
//            user = currentUserService.findCurrentUser(userDetails).orElse(null);
//        }
//
//        CategoryDto category = categoryService.findByTitle(title)
//                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
//
//        // Создаем PageRequest с сортировкой
//        PageRequest pageRequest = PageRequest.of(page, 12, productService.getSort(sort));
//        Page<ProductListDto> products = productService.findByCategoryWithDiscounts(
//                category.getId(), user, pageRequest);
//
//        // Проверка товаров в корзине (только для авторизованных)
//        Map<Long, Integer> cartProductQuantities = new HashMap<>();
//
//        if (user != null) {
//            try {
//                Cart cart = cartService.getOrCreateCart(user.getId());
//                List<CartItemDto> cartItems = cartService.getCartItems(cart.getId());
//
//                for (CartItemDto cartItem : cartItems) {
//                    if (cartItem.getProductId() != null) {
//                        cartProductQuantities.put(cartItem.getProductId(), cartItem.getQuantity());
//                    }
//                }
//            } catch (Exception e) {
//                log.warn("Ошибка при проверке корзины: {}", e.getMessage());
//            }
//        }
//
//        // Добавляем информацию о товарах в корзине в каждый продукт
//        List<ProductListDto> productsWithCartInfo = products.getContent().stream()
//                .map(product -> {
//                    ProductListDto enhancedProduct = new ProductListDto();
//                    BeanUtils.copyProperties(product, enhancedProduct);
//
//                    Integer cartQuantity = cartProductQuantities.get(product.getId());
//                    if (cartQuantity != null && cartQuantity > 0) {
//                        enhancedProduct.setInCart(true);
//                        enhancedProduct.setCartQuantity(cartQuantity);
//                    } else {
//                        enhancedProduct.setInCart(false);
//                        enhancedProduct.setCartQuantity(0);
//                    }
//
//                    return enhancedProduct;
//                })
//                .toList();
//
//        Page<ProductListDto> enhancedProducts = new PageImpl<>(
//                productsWithCartInfo,
//                products.getPageable(),
//                products.getTotalElements()
//        );
//
//        model.addAttribute("category", category);
//        model.addAttribute("products", enhancedProducts);
//        model.addAttribute("cartProductQuantities", cartProductQuantities);
//        model.addAttribute("currentSort", sort); // Передаем текущую сортировку в модель для UI
//
//        log.info("Страница Товаров в категории #{} загружена: {} мс",
//                category.getName(), System.currentTimeMillis() - start);
//        return "catalog/category";
//    }

}
