package ru.fisher.ToolsMarket.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import ru.fisher.ToolsMarket.PostgresTestConfig;
import ru.fisher.ToolsMarket.dto.CartItemDto;
import ru.fisher.ToolsMarket.models.Cart;
import ru.fisher.ToolsMarket.models.CartItem;
import ru.fisher.ToolsMarket.models.Product;
import ru.fisher.ToolsMarket.repository.CartItemRepository;
import ru.fisher.ToolsMarket.repository.CartRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


@SpringBootTest
@ContextConfiguration(initializers = PostgresTestConfig.class)
class CartServiceTest {

    @Autowired
    private CartRepository cartRepository;
    @Autowired
    private CartService cartService;
    @Autowired
    private ProductService productService;
    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private JdbcTemplate jdbc;

    public Product createAndSaveProduct(String productName, BigDecimal price) {
        Product product = Product.builder()
               .name(productName)
               .images(new LinkedHashSet<>())
               .createdAt(Instant.now())
               .updatedAt(Instant.now())
               .price(price)
               .attributeValues(new LinkedHashSet<>())
               .active(true)
               .currency("RUB")
               .categories(new HashSet<>())
               .sku("SKU-" + productName)
               .title("Title-" + productName)
               .shortDescription("short-desc")
               .description("description")
               .build();
        productService.saveEntity(product);
        return product;
    }

    @AfterEach
    void cleanup() {
        jdbc.execute("TRUNCATE TABLE cart_item RESTART IDENTITY CASCADE");
        jdbc.execute("TRUNCATE TABLE cart RESTART IDENTITY CASCADE");
        jdbc.execute("TRUNCATE TABLE product RESTART IDENTITY CASCADE");
    }

    @Test
    void getOrCreateCartCreatesNewCart() {
        // given
        String sessionId = UUID.randomUUID().toString();
        // when
        Cart cart = cartService.getOrCreateCart(null, sessionId);

        // then
        assertThat(cart).isNotNull();
        assertThat(cart.getId()).isNotNull();
        assertThat(cart.getSessionId()).isEqualTo(sessionId);

        // Корзина из БД
        Cart fromDb = cartRepository.findBySessionId(sessionId).orElse(null);
        assertThat(fromDb).isNotNull();
    }

    @Test
    void getOrCreateCartReturnsExistingCart() {
        // given
        String sessionId = UUID.randomUUID().toString();

        Cart existing = new Cart();
        existing.setSessionId(sessionId);
        cartRepository.save(existing);

        // when
        Cart cart = cartService.getOrCreateCart(null, sessionId);

        // then
        assertThat(cart.getId()).isEqualTo(existing.getId());
        assertThat(cartRepository.count()).isEqualTo(1);
    }

    @Test
    void addProductCreatesCartItem() {
        // given
        // создаем корзину
        String sessionId = UUID.randomUUID().toString();
        Cart cart = cartService.getOrCreateCart(null, sessionId);

        // создаем продукт
        Product product = createAndSaveProduct("Test-Product", BigDecimal.valueOf(10000.00));

        // when
        cartService.addProduct(cart.getId(), product.getId());

        // then
        List<CartItem> items = cartItemRepository.findByCartId(cart.getId());
        assertThat(items).hasSize(1);

        CartItem item = items.getFirst();
        assertThat(item.getProduct().getId()).isEqualTo(product.getId());
        assertThat(item.getQuantity()).isEqualTo(1);
        assertThat(item.getUnitPrice().doubleValue())
                .isEqualTo(product.getPrice().doubleValue());
        assertThat(item.getProductName()).isEqualTo("Test-Product");
        assertThat(item.getProductSku()).isEqualTo("SKU-" + product.getName());
    }

    @Test
    void whenProductAddedToEmptyCart_cartContainsOneItemWithSnapshot() {
        // given
        String sessionId = UUID.randomUUID().toString();
        Cart cart = cartService.getOrCreateCart(null, sessionId);
        Product p = createAndSaveProduct("Drill X", BigDecimal.valueOf(10000.00));

        // when — добавляем товар
        cartService.addProduct(cart.getId(), p.getId());

        // then — проверяем публичный API чтения
        List<CartItemDto> items = cartService.getCartItems(cart.getId());

        assertThat(items).hasSize(1);
        CartItemDto item = items.getFirst();

        // — проверяем только публичное поведение
        assertThat(item.getProductId()).isEqualTo(p.getId());
        assertThat(item.getQuantity()).isEqualTo(1);

        // snapshot (contract)
        assertThat(item.getTotalPrice()).isEqualByComparingTo(p.getPrice());
        assertThat(item.getProductName()).isEqualTo("Drill X");
    }

    @Test
    void addProductIncreasesQuantityIfExists() {
        // given
        String sessionId = UUID.randomUUID().toString();
        Cart cart = cartService.getOrCreateCart(null, sessionId);

        // создаем продукт
        Product product = createAndSaveProduct("Test-Product", BigDecimal.valueOf(10000.00));

        // when — добавляем 2 раза один и тот же продукт
        cartService.addProduct(cart.getId(), product.getId());
        cartService.addProduct(cart.getId(), product.getId());

        // then — должен быть 1 CartItem, quantity == 2
        List<CartItem> items = cartItemRepository.findByCartId(cart.getId());
        assertThat(items).hasSize(1);

        CartItem item = items.getFirst();

        assertThat(item.getProduct().getId()).isEqualTo(product.getId());
        assertThat(item.getQuantity()).isEqualTo(2);
    }

    @Test
    void removeProductFromCartDeletesCartItem() {
        String sessionId = UUID.randomUUID().toString();
        Cart cart = cartService.getOrCreateCart(null, sessionId);
        Product product = createAndSaveProduct("Test-Product", BigDecimal.valueOf(10000.00));

        // Добавляем товар в корзину (проверяем что удаляется независимо от quantity)
        cartService.addProduct(cart.getId(), product.getId());
        cartService.addProduct(cart.getId(), product.getId());

        // Проверяем что товар 1
        assertThat(cartItemRepository.findByCartId(cart.getId())).hasSize(1);

        // when — удаляем товар полностью
        cartService.removeProduct(cart.getId(), product.getId());

        // then — CartItem должен исчезнуть даже при quantity > 1
        List<CartItem> items = cartItemRepository.findByCartId(cart.getId());
        assertThat(items).isEmpty();
    }

    @Test
    void decreaseProductQuantityReducesQuantityOrRemovesItem() {
        // given
        String sessionId = UUID.randomUUID().toString();
        Cart cart = cartService.getOrCreateCart(null, sessionId);

        Product product = createAndSaveProduct("Test-Product", BigDecimal.valueOf(10000.00));

        // добавляем 2 раза — quantity = 2
        cartService.addProduct(cart.getId(), product.getId());
        cartService.addProduct(cart.getId(), product.getId());

        // Проверяем что 2 штуки одного товара
        CartItem itemBefore = cartItemRepository.findByCartId(cart.getId()).getFirst();
        assertThat(itemBefore.getQuantity()).isEqualTo(2);

        // when — уменьшаем количество
        cartService.decreaseProductQuantity(cart.getId(), product.getId());

        // then — quantity должно стать 1
        CartItem itemAfter = cartItemRepository.findByCartId(cart.getId()).getFirst();
        assertThat(itemAfter.getQuantity()).isEqualTo(1);

        // when — уменьшаем ещё раз
        cartService.decreaseProductQuantity(cart.getId(), product.getId());

        // then — CartItem должен исчезнуть
        List<CartItem> items = cartItemRepository.findByCartId(cart.getId());
        assertThat(items).isEmpty();
    }

    @Test
    void decreaseProductQuantityForNonExistentItemDoesNothing() {
        // given
        String sessionId = UUID.randomUUID().toString();
        Cart cart = cartService.getOrCreateCart(null, sessionId);
        Product product = createAndSaveProduct("Test-Product", BigDecimal.valueOf(10000.00));

        // when - уменьшаем количество товара, которого нет в корзине
        cartService.decreaseProductQuantity(cart.getId(), product.getId());

        // then - не должно быть ошибок, корзина пустая
        List<CartItem> items = cartItemRepository.findByCartId(cart.getId());
        assertThat(items).isEmpty();
    }

    @Test
    void addProductThrowsWhenCartNotFound() {
        // given
        Long nonExistentCartId = 999L;
        Product product = createAndSaveProduct("Test-Product", BigDecimal.valueOf(10000.00));

        // when & then
        assertThatThrownBy(() -> cartService.addProduct(nonExistentCartId, product.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cart not found");
    }

    @Test
    void addProductThrowsWhenProductNotFound() {
        // given
        String sessionId = UUID.randomUUID().toString();
        Cart cart = cartService.getOrCreateCart(null, sessionId);
        Long nonExistentProductId = 999L;

        // when & then
        assertThatThrownBy(() -> cartService.addProduct(cart.getId(), nonExistentProductId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Product not found");
    }

    @Test
    void getCartItemsReturnsAllItems() {
        // given
        String sessionId = UUID.randomUUID().toString();
        Cart cart = cartService.getOrCreateCart(null, sessionId);

        Product product1 = createAndSaveProduct("Product 1", BigDecimal.valueOf(10000.00));
        Product product2 = createAndSaveProduct("Product 2", BigDecimal.valueOf(10000.00));

        cartService.addProduct(cart.getId(), product1.getId());
        cartService.addProduct(cart.getId(), product2.getId());

        // when
        List<CartItemDto> items = cartService.getCartItems(cart.getId());

        // then
        assertThat(items).hasSize(2);

        CartItemDto item1 = findItemByProductId(items, product1.getId());
        assertThat(item1.getProductName()).isEqualTo("Product 1");
        assertThat(item1.getQuantity()).isEqualTo(1);
        // Общая стоимость = кол-во товара * на стоимость 1-й единицы
        assertThat(item1.getTotalPrice().doubleValue())
                .isEqualTo(product1.getPrice().doubleValue() * item1.getQuantity());
    }

    private CartItemDto findItemByProductId(List<CartItemDto> items, Long productId) {
        return items.stream()
                .filter(item -> item.getProductId().equals(productId))
                .findFirst()
                .orElseThrow(() -> new AssertionError
                        ("Item with productId " + productId + " not found"));
    }

    @Test
    void addProductWithQuantityCreatesCartItemWithCorrectQuantity() {
        // given
        String sessionId = UUID.randomUUID().toString();
        Cart cart = cartService.getOrCreateCart(null, sessionId);
        Product product = createAndSaveProduct("Test-Product", BigDecimal.valueOf(10000.00));
        int quantity = 3;

        // when
        cartService.addProductWithQuantity(cart.getId(), product.getId(), quantity);

        // then
        List<CartItem> items = cartItemRepository.findByCartId(cart.getId());
        assertThat(items).hasSize(1);

        CartItem item = items.getFirst();
        assertThat(item.getProduct().getId()).isEqualTo(product.getId());
        assertThat(item.getQuantity()).isEqualTo(quantity); // Проверяем что quantity = 3
        assertThat(item.getUnitPrice()).isEqualByComparingTo(product.getPrice());
    }

    @Test
    void addProductWithQuantityIncreasesExistingItemQuantity() {
        // given
        String sessionId = UUID.randomUUID().toString();
        Cart cart = cartService.getOrCreateCart(null, sessionId);
        Product product = createAndSaveProduct("Test-Product", BigDecimal.valueOf(10000.00));

        // Сначала добавляем 2 штуки
        cartService.addProduct(cart.getId(), product.getId());
        cartService.addProduct(cart.getId(), product.getId());

        // when - добавляем еще 3 штуки
        cartService.addProductWithQuantity(cart.getId(), product.getId(), 3);

        // then - должно быть 2 + 3 = 5 штук
        CartItem item = cartItemRepository.findByCartIdAndProductId(cart.getId(), product.getId())
                .orElseThrow();
        assertThat(item.getQuantity()).isEqualTo(5);
    }

    @Test
    void addProductWithQuantityZeroDoesNothing() {
        // given
        String sessionId = UUID.randomUUID().toString();
        Cart cart = cartService.getOrCreateCart(null, sessionId);
        Product product = createAndSaveProduct("Test-Product", BigDecimal.valueOf(10000.00));

        // when
        assertThatThrownBy(() -> cartService.addProductWithQuantity(cart.getId(), product.getId(), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Quantity must be positive");

        // then
        List<CartItem> items = cartItemRepository.findByCartId(cart.getId());
        assertThat(items).isEmpty(); // Ничего не добавилось
    }

    @Test
    void addProductWithNegativeQuantityThrowsException() {
        // given
        String sessionId = UUID.randomUUID().toString();
        Cart cart = cartService.getOrCreateCart(null, sessionId);
        Product product = createAndSaveProduct("Test-Product", BigDecimal.valueOf(10000.00));

        // when & then
        assertThatThrownBy(() -> cartService.addProductWithQuantity(cart.getId(), product.getId(), -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Quantity must be positive");
    }
}
