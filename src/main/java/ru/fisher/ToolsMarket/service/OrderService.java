package ru.fisher.ToolsMarket.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import ru.fisher.ToolsMarket.exceptions.InvalidStatusTransitionException;
import ru.fisher.ToolsMarket.exceptions.OrderFinalizedException;
import ru.fisher.ToolsMarket.exceptions.OrderNotFoundException;
import ru.fisher.ToolsMarket.exceptions.OrderValidationException;
import ru.fisher.ToolsMarket.models.*;
import ru.fisher.ToolsMarket.repository.CartItemRepository;
import ru.fisher.ToolsMarket.repository.CartRepository;
import ru.fisher.ToolsMarket.repository.OrderRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;


@Service
@AllArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;

    /**
     * Создаёт заказ на основе корзины:
     *  - копирует cart_items -> order_item (snapshot)
     *  - считает subtotal для каждого item и total для заказа
     *  - очищает корзину
     */
    @Transactional
    public Order createOrder(Long cartId) {
        // проверяем корзину
        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new IllegalArgumentException("Cart not found"));

        List<CartItem> cartItems = cartItemRepository.findByCartId(cart.getId());
        if (cartItems.isEmpty()) {
            throw new IllegalStateException("Cart is empty");
        }

        // создаём заказ
        Order order = new Order();
        order.setOrderNumber(generateOrderNumber());
        order.setStatus(OrderStatus.CREATED);
        order.setCreatedAt(Instant.now());
        order.setUpdatedAt(Instant.now());

        BigDecimal total = BigDecimal.ZERO;

        for (CartItem ci : cartItems) {
            OrderItem oi = new OrderItem();
            oi.setOrder(order);
            oi.setProductId(ci.getProductId());
            oi.setProductName(ci.getProductName());
            oi.setProductSku(ci.getProductSku());
            oi.setUnitPrice(ci.getUnitPrice());
            oi.setQuantity(ci.getQuantity());

            BigDecimal sub = ci.getUnitPrice().multiply(BigDecimal.valueOf(ci.getQuantity()));
            oi.setSubtotal(sub);

            order.getOrderItems().add(oi);
            total = total.add(sub);
        }

        order.setTotalPrice(total);

        // сохраняем заказ (c cascade сохранит order_items)
        Order saved = orderRepository.save(order);

        // очищаем корзину — удаляем items
        cartItemRepository.deleteAll(cartItems);

        return saved;
    }

    @Transactional
    public Order updateStatus(Long orderId, OrderStatus newStatus) {
        validateStatusUpdate(orderId, newStatus);

        Order order = getOrder(orderId);
        validateStatusTransition(order.getStatus(), newStatus);

        order.setStatus(newStatus);
        order.setUpdatedAt(Instant.now());

        Order saved = orderRepository.save(order);
        log.info("Статус заказа обновлен: id={}, номер={}, старый статус={}, новый статус={}",
                orderId, saved.getOrderNumber(), order.getStatus(), newStatus);

        return saved;
    }

    @Transactional(readOnly = true)
    public Order getOrder(Long id) {
        return orderRepository.findByIdWithItems(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<Order> getAllOrders() {
        return orderRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<Order> getOrdersByStatus(OrderStatus status) {
        return orderRepository.findByStatusOrderByCreatedAtDesc(status);
    }

    @Transactional(readOnly = true)
    public long countOrdersByStatus(OrderStatus status) {
        return orderRepository.countByStatus(status);
    }

    @Transactional(readOnly = true)
    public Order findByOrderNumber(Long orderNumber) {
        return orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new OrderNotFoundException(null) {
                    @Override
                    public String getMessage() {
                        return String.format("Заказ с номером %d не найден", orderNumber);
                    }
                });
    }

    @Transactional(readOnly = true)
    public List<Order> searchOrders(String query) {
        // Поиск по SKU товаров в заказе
        return orderRepository.searchByProductSku("%" + query + "%");
    }

    public long countAllOrders() {
        return orderRepository.count();
    }

    public BigDecimal calculateTotalRevenue() {
        return orderRepository.calculateTotalRevenue();
    }

    public void addNote(Long orderId, String note) {
        validateNote(note);

        Order order = getOrder(orderId);
        order.setNote(note);
        order.setUpdatedAt(Instant.now());

        orderRepository.save(order);
    }

    // Метод для получения заказов за последние N дней
    public List<Order> getRecentOrders(int days) {
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        return orderRepository.findByCreatedAtAfterOrderByCreatedAtDesc(since);
    }

    private boolean isValidTransition(OrderStatus from, OrderStatus to) {
        return switch (from) {
            case CREATED ->
                    to == OrderStatus.PAID
                            || to == OrderStatus.PROCESSING
                            || to == OrderStatus.COMPLETED
                            || to == OrderStatus.CANCELLED;

            case PAID ->
                    to == OrderStatus.PROCESSING
                            || to == OrderStatus.COMPLETED
                            || to == OrderStatus.CANCELLED;

            case PROCESSING ->
                    to == OrderStatus.COMPLETED
                            || to == OrderStatus.CANCELLED;

            default -> false; // COMPLETED, CANCELLED
        };
    }

    private void validateStatusUpdate(Long orderId, OrderStatus newStatus) {
        if (orderId == null) {
            throw new OrderValidationException("orderId", "ID заказа не может быть null");
        }
        if (newStatus == null) {
            throw new OrderValidationException("status", "Статус не может быть null");
        }
    }

    private void validateStatusTransition(OrderStatus current, OrderStatus newStatus) {
        // Нельзя менять завершенные или отмененные заказы
        if (current == OrderStatus.COMPLETED || current == OrderStatus.CANCELLED) {
            throw new OrderFinalizedException(current.name());
        }

        // Проверяем корректный переход статуса
        if (!isValidTransition(current, newStatus)) {
            throw new InvalidStatusTransitionException(current.name(), newStatus.name());
        }
    }

    private void validateNote(String note) {
        if (!StringUtils.hasText(note)) {
            throw new OrderValidationException("note", "Примечание не может быть пустым");
        }
        if (note.length() > 1000) {
            throw new OrderValidationException("note",
                    "Примечание слишком длинное (максимум 1000 символов)");
        }
    }

    private Long generateOrderNumber() {
        // Минимальная реализация: millis + random tail (уникальность в DB обеспечена constraint'ом)
        long millis = Instant.now().toEpochMilli();
        long rnd = ThreadLocalRandom.current().nextLong(10_000, 99_999);
        return Long.parseLong(String.valueOf(millis).concat(String.valueOf(rnd)));
    }
}
