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
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;


@Service
@AllArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final OrderAdminJdbcRepository orderAdminJdbc;
    private final DiscountService discountService;
    private final ApplicationEventPublisher eventPublisher;

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

    @Transactional
    public Order createOrder(Long cartId, String note) {
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
                .note(note)
                .status(OrderStatus.CREATED)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .orderItems(new HashSet<>())
                .build();

        BigDecimal total = BigDecimal.ZERO;
        BigDecimal totalDiscount = BigDecimal.ZERO;

        // Скидки пользователя — один запрос на заказ вместо запроса на позицию
        Map<ProductType, BigDecimal> discounts = discountService.getDiscountsForUser(user);

        for (CartItem ci : cartItems) {
            Product product = ci.getProduct();
            Integer quantity = ci.getQuantity();

            // Получаем оригинальную цену (без скидки)
            BigDecimal originalPrice = product.getPrice();

            // Рассчитываем скидку для пользователя
            BigDecimal discountPercentage = discountService.getDiscountPercentage(discounts, product);

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
                order.getUser().getEmail(),
                order.getNote()
        ));

        return saved;
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
