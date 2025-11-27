package ru.fisher.ToolsMarket.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.fisher.ToolsMarket.dto.CartItemDto;
import ru.fisher.ToolsMarket.models.Cart;
import ru.fisher.ToolsMarket.models.CartItem;
import ru.fisher.ToolsMarket.models.Product;
import ru.fisher.ToolsMarket.repository.CartItemRepository;
import ru.fisher.ToolsMarket.repository.CartRepository;
import ru.fisher.ToolsMarket.repository.ProductRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final CartItemRepository cartItemRepository;

    @Transactional
    public Cart getOrCreateCart(Long userId, String sessionId) {
        if (userId != null) {
            return cartRepository.findByUserId(userId)
                    .orElseGet(() -> {
                        Cart c = new Cart();
                        c.setUserId(userId);
                        return cartRepository.save(c);
                    });
        }

        return cartRepository.findBySessionId(sessionId)
                .orElseGet(() -> {
                    Cart c = new Cart();
                    c.setSessionId(sessionId);
                    return cartRepository.save(c);
                });
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
            cartItemRepository.save(existing);
            return;
        }

        // Товара не было — создаём новый CartItem
        CartItem item = new CartItem();
        item.setCart(cart);
        item.setProductId(product.getId());
        item.setProductName(product.getName());
        item.setProductSku(product.getSku());
        item.setUnitPrice(product.getPrice());
        item.setQuantity(1);

        cartItemRepository.save(item);
    }

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

    @Transactional(readOnly = true)
    public List<CartItemDto> getCartItems(Long cartId) {
        // Проверяем что корзина существует
        if (!cartRepository.existsById(cartId)) {
            throw new IllegalArgumentException("Cart not found with id: " + cartId);
        }

        // Получаем все items для корзины
        List<CartItem> items = cartItemRepository.findByCartId(cartId);

        // Преобразуем в DTO
        return items.stream()
                .map(this::convertToDto)
                .toList();
    }


    private CartItemDto convertToDto(CartItem item) {
        return new CartItemDto(
                item.getProductId(),
                item.getProductName(),
                item.getProductSku(),
                item.getUnitPrice(),
                item.getQuantity()
        );
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
        item.setProductId(product.getId());
        item.setProductName(product.getName());
        item.setProductSku(product.getSku());
        item.setUnitPrice(product.getPrice());
        item.setQuantity(quantity);

        cartItemRepository.save(item);
    }
}
