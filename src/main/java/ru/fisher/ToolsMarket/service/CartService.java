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

    /**
     * Получение или создание корзины с учетом пользователя
     */
    @Transactional
    public Cart getOrCreateCart(Long userId, String sessionId) {
        if (userId != null) {
            return cartRepository.findByUserId(userId)
                    .orElseGet(() -> createCartForUser(userId));
        }

        // Анонимная корзина
        return cartRepository.findBySessionId(sessionId)
                .orElseGet(() -> {
                    Cart cart = Cart.builder()
                            .sessionId(sessionId)
                            .build();
                    return cartRepository.save(cart);
                });
    }

    /**
     * Создание корзины для зарегистрированного пользователя
     */
    @Transactional
    public Cart createCartForUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Проверяем, нет ли уже корзины у пользователя
        if (cartRepository.existsByUserId(userId)) {
            throw new IllegalStateException("User already has a cart");
        }

        Cart cart = Cart.builder()
                .user(user)
                .build();

        return cartRepository.save(cart);
    }

    /**
     * Привязка анонимной корзины к пользователю
     */
    @Transactional
    public Cart mergeCartToUser(String sessionId, Long userId) {
        Cart anonymousCart = cartRepository.findBySessionIdWithProducts(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Cart not found"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Проверяем, есть ли у пользователя уже корзина
        Optional<Cart> existingUserCart = cartRepository.findByUserIdWithProducts(userId);

        if (existingUserCart.isPresent()) {
            // Объединяем корзины
            return mergeCarts(anonymousCart, existingUserCart.get(), user);
        } else {
            // Привязываем анонимную корзину к пользователю
            anonymousCart.setUser(user);
            anonymousCart.setSessionId(null); // Очищаем sessionId
            return cartRepository.save(anonymousCart);
        }
    }

    /**
     * Объединение двух корзин
     */
    private Cart mergeCarts(Cart sourceCart, Cart targetCart, User user) {
        Set<CartItem> sourceItems = sourceCart.getItems();
        Set<CartItem> targetItems = targetCart.getItems();

        // Объединяем товары
        for (CartItem sourceItem : sourceItems) {
            Optional<CartItem> existingItem = targetItems.stream()
                    .filter(item -> item.getProduct().getId()
                            .equals(sourceItem.getProduct().getId()))
                    .findFirst();

            if (existingItem.isPresent()) {
                // Увеличиваем количество
                CartItem item = existingItem.get();
                item.setQuantity(item.getQuantity() + sourceItem.getQuantity());
                cartItemRepository.save(item);
            } else {
                // Добавляем новый товар
                sourceItem.setCart(targetCart);
                targetItems.add(sourceItem);
                cartItemRepository.save(sourceItem);
            }
        }

        // Удаляем старую корзину
        cartRepository.delete(sourceCart);

        // Обновляем пользователя
        targetCart.setUser(user);
        return cartRepository.save(targetCart);
    }

    /**
     * Получение корзины пользователя с предзагрузкой товаров
     */
    @Transactional(readOnly = true)
    public Cart getCartWithItems(Long userId) {
        return cartRepository.findWithItemsByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Cart not found for user"));
    }

    /**
     * Получение корзины с полной загрузкой продуктов
     */
    @Transactional(readOnly = true)
    public Cart getCartWithProducts(Long userId) {
        return cartRepository.findByUserIdWithProducts(userId)
                .orElseThrow(() -> new IllegalArgumentException("Cart not found for user"));
    }

    /**
     * Получение корзины с продуктами по сессии
     */
    @Transactional(readOnly = true)
    public Cart getCartWithProductsBySession(String sessionId) {
        return cartRepository.findBySessionIdWithProducts(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Cart not found for session"));
    }

    // Остальные методы обновляем для работы с userId

    @Transactional
    public void addProductToUserCart(Long userId, Long productId) {
        Cart cart = getOrCreateCart(userId, null);
        addProduct(cart.getId(), productId);
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
        Cart cart = getCartWithProducts(userId);
        return convertCartItemsToDto(cart.getItems());
    }

    @Transactional(readOnly = true)
    public List<CartItemDto> getCartItems(Long cartId) {
        Cart cart = cartRepository.findByIdWithProducts(cartId)
                .orElseThrow(() -> new IllegalArgumentException("Cart not found with id: " + cartId));

        return convertCartItemsToDto(cart.getItems());
    }


//    private CartItemDto convertToDto(CartItem item) {
//        return new CartItemDto(
//                item.getProductId(),
//                item.getProductName(),
//                item.getProductSku(),
//                item.setProductSlug(item.getProductSlug()),
//                item.getUnitPrice(),
//                item.getQuantity())
//        );
//    }

//    public CartItemDto convertToDto(CartItem cartItem) {
//        CartItemDto dto = new CartItemDto();
//        dto.setProductId(cartItem.getProductId());
//        dto.setProductName(cartItem.getProductName());
//        dto.setProductSku(cartItem.getProductSku());
//        dto.setUnitPrice(cartItem.getUnitPrice());
//        dto.setQuantity(cartItem.getQuantity());
//
//        // Получаем slug отдельным запросом
//        String slug = getProductTitle(cartItem.getProductId());
//        dto.setProductSlug(slug);
//
//        return dto;
//    }

    /**
     * Получение данных корзины с подсчетом итогов
     */
    @Transactional(readOnly = true)
    public CartDataDto getCartData(Long userId) {
        Cart cart = getCartWithProducts(userId);

        List<CartItemDto> items = convertCartItemsToDto(cart.getItems());

        BigDecimal total = items.stream()
                .map(CartItemDto::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return CartDataDto.builder()
                .items(items)
                .total(total)
                .itemCount(items.size())
                .build();
    }


    @Transactional(readOnly = true)
    public List<CartItemDto> getCartItemsBySession(String sessionId) {
        Cart cart = getCartWithProductsBySession(sessionId);
        return convertCartItemsToDto(cart.getItems());
    }

    /**
     * Конвертация списка CartItem в DTO
     */
    private List<CartItemDto> convertCartItemsToDto(Set<CartItem> cartItems) {
        return cartItems.stream()
                .map(this::convertToDto)
                .toList();
    }

    /**
     * Конвертация одного CartItem в DTO
     */
    private CartItemDto convertToDto(CartItem cartItem) {
        Product product = cartItem.getProduct();

        String title = product != null ? product.getTitle() : cartItem.getProductName();
        String imageUrl = null;
        String imageAlt = null;

        if (product != null && !product.getImages().isEmpty()) {
            // Берем первое изображение (отсортированное по sortOrder)
            ProductImage mainImage = product.getImages().stream().findFirst().orElse(null);
            imageUrl = mainImage.getUrl();
            imageAlt = mainImage.getAlt();
        }

        CartItemDto itemDto = new CartItemDto();
        itemDto.setProductId(product != null ? product.getId() : null);
        itemDto.setProductName(cartItem.getProductName());
        itemDto.setProductSku(cartItem.getProductSku());
        itemDto.setProductTitle(title);
        itemDto.setProductImageUrl(imageUrl);
        itemDto.setProductImageAlt(imageAlt);
        itemDto.setUnitPrice(cartItem.getUnitPrice());
        itemDto.setQuantity(cartItem.getQuantity());
        itemDto.setTotalPrice(cartItem.getUnitPrice()
                .multiply(BigDecimal.valueOf(cartItem.getQuantity())));
        return itemDto;
    }

//    private CartItemDto convertToDtoSimple(CartItem cartItem) {
//        // Получаем товар с изображениями
//        Product product = productRepository.findById(cartItem.getProductId())
//                .orElse(null);
//
//        String title = product != null ? product.getTitle() : null;
//        String imageUrl = null;
//        String imageAlt = null;
//
//        if (product != null && !product.getImages().isEmpty()) {
//            // Берем первое изображение (отсортированное по sortOrder)
//            ProductImage mainImage = product.getImages().getFirst();
//            imageUrl = mainImage.getUrl();
//            imageAlt = mainImage.getAlt();
//        }
//
//        return new CartItemDto(
//                cartItem.getProductId(),
//                cartItem.getProductName(),
//                cartItem.getProductSku(),
//                title,
//                imageUrl,
//                imageAlt,
//                cartItem.getUnitPrice(),
//                cartItem.getQuantity()
//        );
//    }

    @Transactional
    public void removeProduct(Long cartId, Long productId) {
        cartItemRepository.findByCartIdAndProductId(cartId, productId)
                .ifPresent(cartItemRepository::delete);
    }

    public void decreaseProductQuantity(Long cartId, Long productId) {
        // Ищем существующий элемент корзины
        CartItem item = cartItemRepository
                .findByCartIdAndProductId(cartId, productId)
                .orElse(null);

        if (item == null) return; // ничего не делаем

        // Если количество больше 1 → уменьшаем
        if (item.getQuantity() > 1) {
            item.setQuantity(item.getQuantity() - 1);
            cartItemRepository.save(item);
            return;
        }

        // Если был один → удаляем CartItem
        cartItemRepository.delete(item);
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

    /**
     * Обновление количества товара в корзине
     */
    @Transactional
    public void updateQuantity(Long cartId, Long productId, Integer quantity) {
        if (quantity == null || quantity < 0) {
            throw new IllegalArgumentException("Quantity cannot be negative");
        }

        if (quantity == 0) {
            removeProduct(cartId, productId);
            return;
        }

        CartItem item = cartItemRepository.findByCartIdAndProductId(cartId, productId)
                .orElseThrow(() -> new IllegalArgumentException("Item not found in cart"));

        item.setQuantity(quantity);
        cartItemRepository.save(item);
    }

    @Transactional
    public void clearCart(Long cartId) {
        // Удаляем все товары из корзины одним запросом
        cartItemRepository.deleteByCartId(cartId);
    }

    /**
     * DTO для полных данных корзины
     */
    @Getter
    @Setter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CartDataDto {
        private List<CartItemDto> items;
        private BigDecimal total;
        private Integer itemCount;
    }
}
