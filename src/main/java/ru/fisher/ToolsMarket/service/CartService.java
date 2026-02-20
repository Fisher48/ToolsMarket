package ru.fisher.ToolsMarket.service;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.fisher.ToolsMarket.dto.CartItemDto;
import ru.fisher.ToolsMarket.models.*;
import ru.fisher.ToolsMarket.repository.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartService {

    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final CartItemRepository cartItemRepository;
    private final UserRepository userRepository;
    private final DiscountService discountService;

    /**
     * Получение или создание корзины для пользователя
     */
    @Transactional
    public Cart getOrCreateCart(Long userId) {
        return cartRepository.findByUserId(userId)
                .orElseGet(() -> createCartForUser(userId));
    }

    /**
     * Создание корзины для пользователя
     */
    @Transactional
    public Cart createCartForUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        log.info("Found user: id={}, username={}", user.getId(), user.getUsername());

        if (cartRepository.existsByUserId(userId)) {
            throw new IllegalStateException("User already has a cart");
        }

        Cart cart = Cart.builder()
                .user(user)
                .build();

        return cartRepository.save(cart);
    }

    /**
     * Добавление товара в корзину пользователя
     */
    @Transactional
    public void addProductToUserCart(Long userId, Long productId, int quantity) {
        Cart cart = getOrCreateCart(userId);
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));

        Optional<CartItem> existingItem = cartItemRepository.findByCartIdAndProductId(cart.getId(), productId);

        if (existingItem.isPresent()) {
            CartItem item = existingItem.get();
            item.setQuantity(item.getQuantity() + quantity);
            cartItemRepository.save(item);
        } else {
            CartItem item = CartItem.builder()
                    .cart(cart)
                    .product(product)
                    .productName(product.getName())
                    .productSku(product.getSku())
                    .unitPrice(product.getPrice())
                    .quantity(quantity)
                    .build();
            cartItemRepository.save(item);
        }
    }

    /**
     * Удаление товара из корзины
     */
    @Transactional
    public void removeProductFromUserCart(Long userId, Long productId) {
        Cart cart = getOrCreateCart(userId);
        cartItemRepository.findByCartIdAndProductId(cart.getId(), productId)
                .ifPresent(cartItemRepository::delete);
    }

    /**
     * Получение корзины с полной загрузкой продуктов
     */
    @Transactional(readOnly = true)
    public Cart getCartWithProducts(Long userId) {
        return cartRepository.findByUserIdWithProducts(userId)
                .orElseThrow(() -> new IllegalArgumentException("Cart not found for user"));
    }

    @Transactional
    public void addProduct(Long cartId, Long productId) {
        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new IllegalArgumentException("Cart not found"));
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));

        // Ищем существующий элемент корзины
        CartItem existing = cartItemRepository.findByCartIdAndProductId(cartId, productId)
                .orElse(null);

        if (existing != null) {
            existing.setQuantity(existing.getQuantity() + 1);
            cartItemRepository.save(existing); return;
        }

        // Товара не было — создаём новый CartItem
        CartItem item = new CartItem();
        item.setCart(cart);
        item.setProduct(product);
        item.setProductName(product.getName());
        item.setProductSku(product.getSku());
        item.setUnitPrice(product.getPrice());
        item.setQuantity(1);
        cartItemRepository.save(item);
    }

    @Transactional(readOnly = true)
    public List<CartItemDto> getUserCartItems(Long userId) {
        // Получаем пользователя для расчета скидок
        User user = userId != null ?
                userRepository.findById(userId).orElse(null) : null;

        Cart cart = getCartWithProducts(userId);
        return convertCartItemsToDto(cart.getItems(), user);
    }

    @Transactional(readOnly = true)
    public List<CartItemDto> getCartItems(Long cartId) {
        Cart cart = cartRepository.findByIdWithProducts(cartId)
                .orElseThrow(() -> new IllegalArgumentException("Cart not found"));

        // Получаем пользователя из корзины для расчета скидок
        User user = cart.getUser();
        return convertCartItemsToDto(cart.getItems(), user);
    }

    /**
     * Очистка корзины пользователя
     */
    @Transactional
    public void clearUserCart(Long userId) {
        Cart cart = getOrCreateCart(userId);
        cartItemRepository.deleteByCartId(cart.getId());
    }

    public BigDecimal calculateSummary(List<CartItemDto> items) {
        return items.stream()
                .map(item -> item.getUnitPrice()
                        .multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Конвертация CartItem в DTO со скидками
     */
    private List<CartItemDto> convertCartItemsToDto(Set<CartItem> cartItems, User user) {
        return cartItems.stream()
                .map(item -> {
                    Product product = item.getProduct();
                    BigDecimal discountPercentage = discountService.calculateDiscount(user, product);
                    BigDecimal unitPrice = item.getUnitPrice();
                    BigDecimal totalPrice = unitPrice.multiply(BigDecimal.valueOf(item.getQuantity()));

                    BigDecimal discountAmount = discountPercentage
                            .multiply(totalPrice)
                            .divide(BigDecimal.valueOf(100));

                    CartItemDto dto = new CartItemDto();
                    dto.setProductId(product.getId());
                    dto.setProductName(item.getProductName());
                    dto.setProductSku(item.getProductSku());
                    dto.setProductTitle(product.getTitle());

                    if (!product.getImages().isEmpty()) {
                        dto.setProductImageUrl(product.getImages().iterator().next().getUrl());
                    }

                    dto.setUnitPrice(unitPrice);
                    dto.setQuantity(item.getQuantity());
                    dto.setTotalPrice(totalPrice);
                    dto.setTotalPriceWithDiscount(totalPrice.subtract(discountAmount));
                    dto.setDiscountAmount(discountAmount);
                    dto.setDiscountPercentage(discountPercentage);

                    return dto;
                })
                .toList();
    }

    @Transactional
    public void removeProduct(Long cartId, Long productId) {
        cartItemRepository.findByCartIdAndProductId(cartId, productId)
                .ifPresent(cartItemRepository::delete);
    }

    /**
     * Уменьшение количества товара
     */
    @Transactional
    public void decreaseProductInUserCart(Long userId, Long productId) {
        Cart cart = getOrCreateCart(userId);
        Optional<CartItem> itemOpt = cartItemRepository.findByCartIdAndProductId(cart.getId(), productId);

        itemOpt.ifPresent(item -> {
            if (item.getQuantity() > 1) {
                item.setQuantity(item.getQuantity() - 1);
                cartItemRepository.save(item);
            } else {
                cartItemRepository.delete(item);
            }
        });
    }

    @Transactional
    public void addProductWithQuantity(Long cartId, Long productId, Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }

        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new IllegalArgumentException("Cart not found"));

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));

        CartItem existing = cartItemRepository.findByCartIdAndProductId(cartId, productId)
                .orElse(null);

        if (existing != null) {
            existing.setQuantity(existing.getQuantity() + quantity);
            cartItemRepository.save(existing);
            return;
        }

        // Создаем новый CartItem с указанным количеством
        CartItem item = new CartItem();
        item.setCart(cart);
        item.setProduct(product);
        item.setProductName(product.getName());
        item.setProductSku(product.getSku());
        item.setUnitPrice(product.getPrice());
        item.setQuantity(quantity);

        cartItemRepository.save(item);
    }


    @Transactional
    public void clearCart(Long cartId) {
        // Удаляем все товары из корзины одним запросом
        cartItemRepository.deleteByCartId(cartId);
    }

    public boolean isProductInCart(Long cartId, Long productId) {
        return cartItemRepository.existsByCartIdAndProductId(cartId, productId);
    }

    public int getProductQuantityInCart(Long cartId, Long productId) {
        return cartItemRepository.findByCartIdAndProductId(cartId, productId)
                .map(CartItem::getQuantity)
                .orElse(0);
    }

}
