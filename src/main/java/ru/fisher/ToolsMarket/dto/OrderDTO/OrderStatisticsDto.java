package ru.fisher.ToolsMarket.dto.OrderDTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatisticsDto {
    private long newOrdersCount;
    private long paidOrdersCount;
    private long completedOrdersCount;
    private long cancelledOrdersCount;
    private long totalOrdersCount;
}
