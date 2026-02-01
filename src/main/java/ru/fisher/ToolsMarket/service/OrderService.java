package ru.fisher.ToolsMarket.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import ru.fisher.ToolsMarket.dto.OrderCreatedEvent;
import ru.fisher.ToolsMarket.dto.OrderItemDto;
import ru.fisher.ToolsMarket.dto.OrderSummaryDto;
import ru.fisher.ToolsMarket.exceptions.InvalidStatusTransitionException;
import ru.fisher.ToolsMarket.exceptions.OrderFinalizedException;
import ru.fisher.ToolsMarket.exceptions.OrderNotFoundException;
import ru.fisher.ToolsMarket.exceptions.OrderValidationException;
import ru.fisher.ToolsMarket.models.*;
import ru.fisher.ToolsMarket.repository.CartItemRepository;
import ru.fisher.ToolsMarket.repository.CartRepository;
import ru.fisher.ToolsMarket.repository.OrderRepository;
import ru.fisher.ToolsMarket.repository.UserRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;


@Service
@AllArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final UserRepository userRepository;
    private final DiscountService discountService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Создание заказа из корзины пользователя
     */
    @Transactional
    public Order createOrderFromUserCart(Long userId) {
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Cart not found"));

        return createOrder(cart.getId());
    }

    /**
     * Получение заказов пользователя
     */
    @Transactional(readOnly = true)
    public List<Order> getUserOrders(Long userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /**
     * Получение конкретного заказа пользователя
     */
    @Transactional(readOnly = true)
    public Order getUserOrder(Long orderId, Long userId) {
        return orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    @Transactional(readOnly = true)
    public Order getOrderWithProducts(Long id) {
        return orderRepository.findByIdWithItemsAndProduct(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<Order> getUserOrdersWithItems(Long userId) {
        return orderRepository.findByUserIdWithItems(userId);
    }

    /**
     * Получение заказов пользователя по статусу
     */
    @Transactional(readOnly = true)
    public List<Order> getUserOrdersByStatus(Long userId, OrderStatus status) {
        return orderRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, status);
    }

    @Transactional
    public Order createOrder(Long cartId) {
        Cart cart = cartRepository.findByIdWithProducts(cartId)
                .orElseThrow(() -> new IllegalArgumentException("Cart not found"));

        if (cart.getUser() == null) {
            throw new IllegalStateException("Cannot create order from anonymous cart");
        }

        Set<CartItem> cartItems = cart.getItems();
        if (cartItems.isEmpty()) {
            throw new IllegalStateException("Cart is empty");
        }

        User user = cart.getUser();
        Order order = Order.builder()
                .orderNumber(generateOrderNumber(user.getId()))
                .user(user)
                .status(OrderStatus.CREATED)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .orderItems(new HashSet<>())
                .build();

        BigDecimal total = BigDecimal.ZERO;
        BigDecimal totalDiscount = BigDecimal.ZERO;

        for (CartItem ci : cartItems) {
            Product product = ci.getProduct();
            Integer quantity = ci.getQuantity();

            // Получаем оригинальную цену (без скидки)
            BigDecimal originalPrice = product.getPrice();

            // Рассчитываем скидку для пользователя
            BigDecimal discountPercentage = discountService.calculateDiscount(user, product);

            // Создаем OrderItem с учетом скидки
            OrderItem oi = OrderItem.createOrderItem(
                    product,
                    ci.getProductName(),
                    ci.getProductSku(),
                    quantity,
                    originalPrice,           // Исходная цена
                    originalPrice,           // originalUnitPrice (та же цена без скидки)
                    discountPercentage       // Процент скидки
            );

            // Если есть скидка, пересчитываем
            if (discountPercentage.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal discountPerUnit = originalPrice
                        .multiply(discountPercentage)
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

                BigDecimal itemDiscount = discountPerUnit.multiply(BigDecimal.valueOf(quantity));
                totalDiscount = totalDiscount.add(itemDiscount);
            }

            total = total.add(oi.getSubtotal());
            oi.setOrder(order);
            order.addOrderItem(oi);
        }

        order.setTotalPrice(total);

        // Сохраняем информацию о скидке
//        if (totalDiscount.compareTo(BigDecimal.ZERO) > 0) {
//            String note = String.format("Скидка по заказу: %.2f RUB", totalDiscount);
//            order.setNote(order.getNote() != null ?
//                    order.getNote() + "\n" + note : note);
//        }

        Order saved = orderRepository.save(order);

        cart.clear();
        cartRepository.save(cart);

        log.info("Заказ создан: id={}, номер={}, цена={}, скидка={}",
                order.getId(), order.getOrderNumber(), order.getTotalPrice(), totalDiscount);

        eventPublisher.publishEvent(new OrderCreatedEvent(
                order.getId(),
                order.getOrderNumber(),
                order.getOrderItems().stream()
                        .map(OrderItemDto::fromEntity).toList(),
                order.getTotalPrice(),
                order.getUser().getEmail()
        ));

        return saved;
    }

    public void cancelOrder(Long orderId, Long userId) {
        Order order = getUserOrder(orderId, userId);

        // Проверяем, можно ли отменить заказ
        if (order.getStatus() != OrderStatus.CREATED && order.getStatus() != OrderStatus.PROCESSING) {
            throw new IllegalArgumentException("Невозможно отменить заказ в текущем статусе");
        }

        order.setStatus(OrderStatus.CANCELLED);
        order.setUpdatedAt(Instant.now());
        orderRepository.save(order);
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
                    to == OrderStatus.PROCESSING
                            || to == OrderStatus.PAID
                            || to == OrderStatus.COMPLETED
                            || to == OrderStatus.CANCELLED;

            case PROCESSING ->
                    to == OrderStatus.PAID
                            || to == OrderStatus.COMPLETED
                            || to == OrderStatus.CANCELLED;

            case PAID ->
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

    private Long generateOrderNumber(Long userId) {
        LocalDateTime now = LocalDateTime.now();

        // 1. Дата и время (10 цифр): YYMMDDHHmm
        String dateTimePart = DateTimeFormatter.ofPattern("yyMMddHHmm").format(now);

        // 2. ID пользователя (до 4 цифр)
        String userIdPart = String.format("%04d", userId % 10000);

        // 3. Рандом (2 цифры) для уникальности
        String randomPart = String.format("%02d", ThreadLocalRandom.current().nextInt(100));

        // Объединяем
        String numberStr = dateTimePart + userIdPart + randomPart;

        return Long.parseLong(numberStr); // Пример: 2412151830123456
    }

    public List<Order> findOrdersByUserId(Long userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    // Добавляем новый метод для получения заказа с расчетом скидок
    public OrderSummaryDto getOrderSummary(Long orderId, Long userId) {
        Order order = getUserOrder(orderId, userId);

        BigDecimal totalDiscount = order.getOrderItems().stream()
                .map(item -> {
                    BigDecimal originalPrice = item.getProduct().getPrice()
                            .multiply(BigDecimal.valueOf(item.getQuantity()));
                    return originalPrice.subtract(item.getSubtotal());
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return OrderSummaryDto.builder()
                .order(order)
                .totalDiscount(totalDiscount)
                .originalTotal(order.getTotalPrice().add(totalDiscount))
                .build();
    }

}
