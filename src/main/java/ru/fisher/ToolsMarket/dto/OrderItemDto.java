package ru.fisher.ToolsMarket.dto;

import lombok.Getter;
import lombok.Setter;
import ru.fisher.ToolsMarket.models.OrderItem;
import ru.fisher.ToolsMarket.models.Product;
import ru.fisher.ToolsMarket.models.ProductImage;

import java.math.BigDecimal;

@Getter
@Setter
public class OrderItemDto {
    private Long productId;
    private String productName;
    private String productSku;
    private String productTitle; // ← для URL страницы товара
    private String productImageUrl; // ← URL изображения товара
    private String productImageAlt; // ← alt текст изображения
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;
    private BigDecimal originalPrice; // Цена без скидки

    // Скидка на момент заказа
    private BigDecimal discountPercentage;
    private BigDecimal discountAmount;
    private boolean hasDiscount;

    // НЕ ИСПОЛЬЗУЕМ DiscountService - работаем только с сохраненными данными
    public static OrderItemDto fromEntity(OrderItem item) {
        OrderItemDto dto = new OrderItemDto();
        dto.setProductId(item.getProduct().getId());
        dto.setProductName(item.getProductName());
        dto.setProductSku(item.getProductSku());
        dto.setQuantity(item.getQuantity());
        dto.setUnitPrice(item.getUnitPrice());
        dto.setSubtotal(item.getSubtotal());

        // Получаем данные из связанного Product
        Product product = item.getProduct();
        dto.setProductTitle(product.getTitle() != null ?
                product.getTitle() : product.getName());

        // Получаем изображение
        if (!product.getImages().isEmpty()) {
            ProductImage mainImage = product.getImages().stream().findFirst().orElse(null);
            dto.setProductImageUrl(mainImage.getUrl());
            dto.setProductImageAlt(mainImage.getAlt() != null ?
                    mainImage.getAlt() : product.getName());
        }

        // Используем только сохраненные данные из БД
        if (item.getOriginalUnitPrice() != null) {
            dto.setOriginalPrice(item.getOriginalUnitPrice());
            dto.setHasDiscount(item.isHasDiscount());

            if (item.isHasDiscount() && item.getDiscountAmount() != null) {
                dto.setDiscountAmount(item.getDiscountAmount());
                dto.setDiscountPercentage(item.getDiscountPercentage());
            } else {
                // Если нет скидки
                dto.setOriginalPrice(item.getUnitPrice());
                dto.setHasDiscount(false);
            }
        } else {
            // Защита на случай если миграция не сработала
            dto.setOriginalPrice(item.getUnitPrice());
            dto.setHasDiscount(false);
        }

        return dto;
    }

    // Дополнительный метод для расчета суммы без скидки
    public BigDecimal getTotalWithoutDiscount() {
        return originalPrice != null
                ? originalPrice.multiply(BigDecimal.valueOf(quantity))
                : unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
