package ru.fisher.ToolsMarket.controller.rest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import ru.fisher.ToolsMarket.dto.CartItemDto;
import ru.fisher.ToolsMarket.dto.CartRequest;
import ru.fisher.ToolsMarket.dto.CartResponse;
import ru.fisher.ToolsMarket.models.Cart;
import ru.fisher.ToolsMarket.models.User;
import ru.fisher.ToolsMarket.service.CartService;
import ru.fisher.ToolsMarket.service.UserService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
@Slf4j
public class CartRestController {

    private final CartService cartService;
    private final UserService userService;

    @PostMapping("/add")
    public ResponseEntity<?> add(@RequestBody CartRequest request,
                                 @AuthenticationPrincipal UserDetails userDetails) {

        log.info("Add to cart request: productId={}, quantity={}",
                request.productId(), request.quantity());

        try {
            if (request.productId() == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Product ID is required"));
            }

            Long userId = getUserId(userDetails);

            Cart cart = cartService.getOrCreateCart(userId);

            cartService.addProductWithQuantity(
                    cart.getId(),
                    request.productId(),
                    request.quantity() != null ? request.quantity() : 1
            );

            return ResponseEntity.ok(buildResponse(cart, userId));

        } catch (Exception e) {
            log.error("Error adding to cart: ", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/decrease")
    public ResponseEntity<?> decrease(@RequestBody CartRequest request,
                                      @AuthenticationPrincipal UserDetails userDetails) {

        log.info("Decrease cart request: productId={}", request.productId());

        try {
            if (request.productId() == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Product ID is required"));
            }

            Long userId = getUserId(userDetails);

            Cart cart = cartService.getOrCreateCart(userId);
            cartService.decreaseProductInUserCart(userId, request.productId());

            return ResponseEntity.ok(buildResponse(cart, userId));

        } catch (Exception e) {
            log.error("Error decreasing cart item: ", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/remove")
    public ResponseEntity<?> remove(@RequestBody CartRequest request,
                                    @AuthenticationPrincipal UserDetails userDetails) {

        log.info("Remove from cart request: productId={}", request.productId());

        try {
            if (request.productId() == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Product ID is required"));
            }

            Long userId = getUserId(userDetails);

            Cart cart = cartService.getOrCreateCart(userId);
            cartService.removeProduct(cart.getId(), request.productId());

            return ResponseEntity.ok(buildResponse(cart, userId));

        } catch (Exception e) {
            log.error("Error removing from cart: ", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/state")
    public ResponseEntity<?> state(@AuthenticationPrincipal UserDetails userDetails) {

        try {
            Long userId = getUserId(userDetails);

            Cart cart = cartService.getOrCreateCart(userId);
            return ResponseEntity.ok(buildResponse(cart, userId));

        } catch (Exception e) {
            log.error("Error getting cart state: ", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // ===== Вспомогательные методы =====

    private CartResponse buildResponse(Cart cart, Long userId) {
        List<CartItemDto> items = userId != null
                ? cartService.getUserCartItems(userId)
                : cartService.getCartItems(cart.getId());

        // Рассчитываем общее количество
        int totalQty = items.stream()
                .mapToInt(CartItemDto::getQuantity)
                .sum();

        // Рассчитываем суммы с использованием BigDecimal для точности
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalDiscount = BigDecimal.ZERO;
        BigDecimal totalWithDiscount = BigDecimal.ZERO;

        for (CartItemDto item : items) {
            // Получаем значения с проверкой на null
            BigDecimal unitPrice = item.getUnitPrice() != null ?
                    item.getUnitPrice() : BigDecimal.ZERO;
            BigDecimal unitPriceWithDiscount = item.getUnitPriceWithDiscount() != null ?
                    item.getUnitPriceWithDiscount() : unitPrice;
            Integer quantity = item.getQuantity() != null ? item.getQuantity() : 0;

            // Рассчитываем суммы для этого товара
            BigDecimal itemTotalPrice = unitPrice.multiply(BigDecimal.valueOf(quantity));
            BigDecimal itemTotalPriceWithDiscount = unitPriceWithDiscount.multiply(BigDecimal.valueOf(quantity));
            BigDecimal itemDiscount = itemTotalPrice.subtract(itemTotalPriceWithDiscount);

            // Добавляем к общим суммам
            totalAmount = totalAmount.add(itemTotalPrice);
            totalWithDiscount = totalWithDiscount.add(itemTotalPriceWithDiscount);
            totalDiscount = totalDiscount.add(itemDiscount);
        }

        // Создаем мапу productId -> quantity для быстрого доступа
        Map<Long, Integer> quantities = items.stream()
                .collect(Collectors.toMap(
                        CartItemDto::getProductId,
                        CartItemDto::getQuantity,
                        (a, b) -> b // Если дублируются, берем последний
                ));

        log.debug("Cart response: items={}, totalQty={}, totalAmount={}, totalDiscount={}, totalWithDiscount={}",
                items.size(), totalQty, totalAmount, totalDiscount, totalWithDiscount);

        return new CartResponse(
                items,
                totalQty,
                quantities,
                totalAmount.doubleValue(),
                totalDiscount.doubleValue(),
                totalWithDiscount.doubleValue()
        );
    }

    private Long getUserId(UserDetails userDetails) {
        if (userDetails == null) return null;

        try {
            return userService.findByUsername(userDetails.getUsername())
                    .map(User::getId)
                    .orElse(null);
        } catch (Exception e) {
            log.error("Error getting user ID: ", e);
            return null;
        }
    }

}

