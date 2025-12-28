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

    // Новый конструктор - работает с новыми OrderItem (со связью Product)
    public static OrderItemDto fromEntity(OrderItem orderItem) {
        OrderItemDto dto = new OrderItemDto();
        dto.setProductId(orderItem.getProduct().getId());
        dto.setProductName(orderItem.getProductName());
        dto.setProductSku(orderItem.getProductSku());
        dto.setQuantity(orderItem.getQuantity());
        dto.setUnitPrice(orderItem.getUnitPrice());
        dto.setSubtotal(orderItem.getSubtotal());

        // Получаем данные из связанного Product
        Product product = orderItem.getProduct();
        if (product != null) {
            dto.setProductTitle(product.getTitle() != null ?
                    product.getTitle() : product.getName());

            // Получаем изображение
            if (!product.getImages().isEmpty()) {
                ProductImage mainImage = product.getImages().stream().findFirst().orElse(null);
                dto.setProductImageUrl(mainImage.getUrl());
                dto.setProductImageAlt(mainImage.getAlt() != null ?
                        mainImage.getAlt() : product.getName());
            }
        }

        return dto;
    }

    // Конструктор из сущности OrderItem
    public static OrderItemDto fromEntity(OrderItem orderItem, ProductDto product) {
        OrderItemDto dto = new OrderItemDto();
        dto.setProductId(orderItem.getProduct().getId());
        dto.setProductName(orderItem.getProductName());
        dto.setProductSku(orderItem.getProductSku());
        dto.setQuantity(orderItem.getQuantity());
        dto.setUnitPrice(orderItem.getUnitPrice());
        dto.setSubtotal(orderItem.getSubtotal());

        // Если передали Product, заполняем дополнительные поля
        if (product != null) {
            dto.setProductTitle(product.getTitle());
            // Получаем первое изображение товара
            if (!product.getImages().isEmpty()) {
                ProductImageDto mainImage = product.getImages().getFirst();
                dto.setProductImageUrl(mainImage.getUrl());
                dto.setProductImageAlt(mainImage.getAlt() != null ?
                        mainImage.getAlt() : product.getName());
            }
        }

        return dto;
    }
}
