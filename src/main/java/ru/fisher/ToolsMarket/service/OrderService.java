package ru.fisher.ToolsMarket.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import ru.fisher.ToolsMarket.dto.OrderDTO.OrderAdminDto;
import ru.fisher.ToolsMarket.dto.OrderDTO.OrderCreatedEvent;
import ru.fisher.ToolsMarket.dto.OrderDTO.OrderItemDto;
import ru.fisher.ToolsMarket.dto.OrderDTO.OrderStatisticsDto;
import ru.fisher.ToolsMarket.dto.UserDTO.UserFilterDto;
import ru.fisher.ToolsMarket.exceptions.OrderNotFoundException;
import ru.fisher.ToolsMarket.exceptions.OrderValidationException;
import ru.fisher.ToolsMarket.models.*;
import ru.fisher.ToolsMarket.service.order.*;
import ru.fisher.ToolsMarket.repository.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;


@Service
@AllArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final OrderAdminJdbcRepository orderAdminJdbc;
    private final DiscountService discountService;
    private final ApplicationEventPublisher eventPublisher;
    private final OrderNumberGenerator orderNumberGenerator;

    /**
     * Создание заказа из корзины пользователя
     */
    @Transactional
    public Order createOrderFromUserCart(Long userId, String note) {
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Cart not found"));

        return createOrder(cart.getId(), note);
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
        return orderRepository.findByUserIdAndStatusWithProducts(userId, status);
    }

    // Программа следует за структурой выхода — Order:
    // 1. Когда выход тривиален? Корзина не найдена / анонимная / пустая — заказа нет.
    // 2. Какова голова? Пустой Order без позиций (orderHeaderOf).
    // 3. Из чего хвост? Список позиций, который возвращает orderItemsOf
    //    (голова — orderItemOf позиция, хвост — рекурсия по остатку корзины);
    //    тоталы — по готовым позициям (totalOf/totalDiscountOf).
    @Transactional
    public Order createOrder(Long cartId, String note) {
        // 1: тривиальный случай — заказа не будет
        Cart cart = cartRepository.findByIdWithProducts(cartId)
                .orElseThrow(() -> new IllegalArgumentException("Cart not found"));

        if (cart.getUser() == null) {
            throw new IllegalStateException("Cannot create order from anonymous cart");
        }

        if (cart.getItems().isEmpty()) {
            throw new IllegalStateException("Cart is empty");
        }

        User user = cart.getUser();

        // 2: голова выхода
        Order order = orderHeaderOf(user, note);

        // Скидки пользователя — один запрос на заказ вместо запроса на позицию
        Map<ProductType, BigDecimal> discounts = discountService.getDiscountsForUser(user);

        // 3: позиции множества выстраиваем в список — у рекурсии появится «голова»
        List<OrderItem> items = orderItemsOf(new ArrayList<>(cart.getItems()), 0, discounts);

        // Готовые позиции привязываем к заказу (двусторонняя связь order/OrderItem)
        for (OrderItem item : items) {
            item.setOrder(order);
            order.addOrderItem(item);
        }

        // Тотал — сумма позиций, а не отдельный аккумулятор при обходе корзины
        order.setTotalPrice(totalOf(order.getOrderItems()));

        Order saved = orderRepository.save(order);

        cart.clear();
        cartRepository.save(cart);

        log.info("Заказ создан: id={}, номер={}, цена={}, скидка={}",
                order.getId(), order.getOrderNumber(), order.getTotalPrice(),
                totalDiscountOf(order.getOrderItems()));

        eventPublisher.publishEvent(orderCreatedEventOf(order));

        return saved;
    }

    /** Голова выхода: Order без позиций. */
    private Order orderHeaderOf(User user, String note) {
        return Order.builder()
                .orderNumber(orderNumberGenerator.next(user.getId()))
                .user(user)
                .note(note)
                .status(OrderStatus.CREATED)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .orderItems(new HashSet<>())
                .build();
    }

    /**
     * Строит список позиций заказа по списку корзины: позиция с headIndex
     * (голова) плюс всё, что вернет рекурсия на остатке (хвост).
     * Позиции кончились — возвращается пустой список, это база рекурсии.
     * @param discounts скидки пользователя — считаются один раз на заказ
     */
    private List<OrderItem> orderItemsOf(List<CartItem> lines, int headIndex,
                                         Map<ProductType, BigDecimal> discounts) {
        if (headIndex == lines.size()) {
            return List.of();
        }

        List<OrderItem> tail = orderItemsOf(lines, headIndex + 1, discounts);

        List<OrderItem> result = new ArrayList<>(tail.size() + 1);
        result.add(orderItemOf(lines.get(headIndex), discounts));
        result.addAll(tail);
        return result;
    }

    /** Позиция заказа из одной строки корзины, с учётом скидки пользователя. */
    private OrderItem orderItemOf(CartItem ci, Map<ProductType, BigDecimal> discounts) {
        Product product = ci.getProduct();

        // Получаем оригинальную цену (без скидки)
        BigDecimal originalPrice = product.getPrice();

        // Рассчитываем скидку для пользователя
        BigDecimal discountPercentage =
                discountService.getDiscountPercentage(discounts, product);

        return OrderItem.createOrderItem(
                product,
                ci.getProductName(),
                ci.getProductSku(),
                ci.getQuantity(),
                originalPrice,           // Исходная цена
                originalPrice,           // originalUnitPrice (та же цена без скидки)
                discountPercentage       // Процент скидки
        );
    }

    /** Стоимость заказа — сумма subtotal по позициям. */
    private BigDecimal totalOf(Set<OrderItem> items) {
        BigDecimal total = BigDecimal.ZERO;
        for (OrderItem item : items) {
            total = total.add(item.getSubtotal());
        }
        return total;
    }

    /** Сумма скидок по позициям заказа. */
    private BigDecimal totalDiscountOf(Set<OrderItem> items) {
        BigDecimal totalDiscount = BigDecimal.ZERO;
        for (OrderItem item : items) {
            totalDiscount = totalDiscount.add(item.getDiscountAmount());
        }
        return totalDiscount;
    }

    /** Данные письма о заказе собираются из готового заказа. */
    private OrderCreatedEvent orderCreatedEventOf(Order order) {
        return new OrderCreatedEvent(
                order.getId(),
                order.getOrderNumber(),
                order.getOrderItems().stream().map(OrderItemDto::fromEntity).toList(),
                order.getTotalPrice(),
                order.getUser().getEmail(),
                order.getNote()
        );
    }

    @Transactional
    public void cancelOrder(Long orderId, Long userId) {
        Order order = getUserOrder(orderId, userId);
        OrderStateFactory.of(order).cancel();
        orderRepository.save(order);
    }

    @Transactional
    public Order updateStatus(Long orderId, OrderStatus newStatus) {
        validateStatusUpdate(orderId, newStatus);

        Order order = getOrder(orderId);
        OrderStateFactory.of(order).moveTo(newStatus);

        Order saved = orderRepository.save(order);
        log.debug("Статус заказа обновлен: id={}, номер={}, новый статус={}",
                orderId, saved.getOrderNumber(), newStatus);

        return saved;
    }

    @Transactional(readOnly = true)
    public Order getOrder(Long id) {
        return orderRepository.findByIdWithItems(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    public void addNote(Long orderId, String note) {
        validateNote(note);

        Order order = getOrder(orderId);
        order.setNote(note);
        order.setUpdatedAt(Instant.now());

        orderRepository.save(order);
    }

    private void validateStatusUpdate(Long orderId, OrderStatus newStatus) {
        if (orderId == null) {
            throw new OrderValidationException("orderId", "ID заказа не может быть null");
        }
        if (newStatus == null) {
            throw new OrderValidationException("status", "Статус не может быть null");
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

    @Transactional(readOnly = true)
    public List<OrderAdminDto> getOrdersForAdmin(String status, String search, Long userId) {
        long start = System.currentTimeMillis();
        List<OrderAdminDto> orders = orderAdminJdbc.findOrdersForAdmin(status, search, userId);
        log.debug("Загрузка заказов для админки: {} записей, {} мс",
                orders.size(), System.currentTimeMillis() - start);
        return orders;
    }

    @Transactional(readOnly = true)
    public OrderStatisticsDto getOrderStatistics(String status, String search, Long userId) {
        return orderAdminJdbc.getOrderStatistics(status, search, userId);
    }

    @Transactional(readOnly = true)
    public List<UserFilterDto> getUsersForOrderFilter() {
        return orderAdminJdbc.findUsersWithOrders();
    }

}
