package ru.fisher.ToolsMarket.models;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@Table(name = "\"order\"")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_number", nullable = false, unique = true)
    private Long orderNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.CREATED;

    @Column(name = "total_price", precision = 12, scale = 2, nullable = false)
    private BigDecimal totalPrice;

    @Column(columnDefinition = "text")
    private String note;

    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL,
            orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<OrderItem> orderItems = new ArrayList<>();

    // Helper методы с проверкой на null
    public void addOrderItem(OrderItem item) {
        if (orderItems == null) {
            orderItems = new ArrayList<>();
        }
        orderItems.add(item);
        item.setOrder(this);
    }

    public void removeOrderItem(OrderItem item) {
        if (orderItems != null) {
            orderItems.remove(item);
            item.setOrder(null);
        }
    }

    public int getTotalItemsCount() {
        if (orderItems == null || orderItems.isEmpty()) {
            return 0;
        }
        return orderItems.stream()
                .mapToInt(OrderItem::getQuantity)
                .sum();
    }

    public boolean belongsToUser(Long userId) {
        return user != null && user.getId().equals(userId);
    }
}
