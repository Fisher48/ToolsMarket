package ru.fisher.ToolsMarket.dto.OrderDTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderAdminDto {
    private Long id;
    private Long orderNumber;
    private String status;
    private BigDecimal totalPrice;
    private Instant createdAt;
    private String note;
    private Long userId;
    private String userName;
    private String userEmail;
    private Long itemsCount;
    private Long productsCount;

    public String getStatusDisplay() {
        return switch (status) {
            case "CREATED" -> "Создан";
            case "PAID" -> "Оплачен";
            case "PROCESSING" -> "В обработке";
            case "COMPLETED" -> "Завершен";
            case "CANCELLED" -> "Отменен";
            default -> status;
        };
    }
}
