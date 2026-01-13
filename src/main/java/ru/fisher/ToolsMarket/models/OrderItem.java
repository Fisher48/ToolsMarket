package ru.fisher.ToolsMarket.models;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Builder
@Entity
@Getter
@Setter
@Table(name = "order_item")
@AllArgsConstructor
@NoArgsConstructor
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)  // Должно быть LAZY
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(name = "product_name", nullable = false, length = 512)
    private String productName;

    @Column(name = "product_sku", nullable = false, length = 100)
    private String productSku;

    private Integer quantity;

//    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
//    private ProductAttributeValue productAttributeValue;

    @Column(name = "unit_price", precision = 12, scale = 2, nullable = false)
    private BigDecimal unitPrice;

    @Column(precision = 12, scale = 2, nullable = false)
    private BigDecimal subtotal;

    // Новые поля для сохранения исторических цен
    @Column(name = "original_unit_price", precision = 10, scale = 2, nullable = true)
    private BigDecimal originalUnitPrice;

    @Column(name = "discount_percentage", nullable = true)
    private BigDecimal discountPercentage;

    @Column(name = "discount_amount", precision = 10, scale = 2, nullable = true)
    private BigDecimal discountAmount;

    @Column(name = "has_discount", nullable = true)
    private boolean hasDiscount = false;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    // связь с заказом
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Builder
    public static OrderItem createOrderItem(Product product, String productName, String productSku,
                                            Integer quantity, BigDecimal unitPrice,
                                            BigDecimal originalUnitPrice, BigDecimal discountPercentage) {
        OrderItem item = new OrderItem();
        item.product = product;
        item.productName = productName;
        item.productSku = productSku;
        item.quantity = quantity;
        item.unitPrice = unitPrice;
        item.originalUnitPrice = originalUnitPrice != null ? originalUnitPrice : unitPrice;

        // Рассчитываем скидку если есть
        BigDecimal finalOriginalPrice = item.originalUnitPrice;
        if (discountPercentage != null && discountPercentage.compareTo(BigDecimal.ZERO) > 0) {
            item.hasDiscount = true;
            item.discountPercentage = discountPercentage;

            // Рассчитываем сумму скидки за единицу
            BigDecimal discountPerUnit = finalOriginalPrice
                    .multiply(discountPercentage)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

            item.discountAmount = discountPerUnit.multiply(BigDecimal.valueOf(quantity));
            item.unitPrice = finalOriginalPrice.subtract(discountPerUnit);
        } else {
            item.hasDiscount = false;
            item.discountPercentage = BigDecimal.ZERO;
            item.discountAmount = BigDecimal.ZERO;
            item.unitPrice = finalOriginalPrice; // Без скидки
        }

        item.subtotal = item.unitPrice.multiply(BigDecimal.valueOf(quantity));
        item.createdAt = Instant.now();

        return item;
    }
}
