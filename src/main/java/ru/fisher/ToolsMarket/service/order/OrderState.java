package ru.fisher.ToolsMarket.service.order;

import ru.fisher.ToolsMarket.models.Order;
import ru.fisher.ToolsMarket.models.OrderStatus;

public sealed interface OrderState
        permits CreatedOrder, ProcessingOrder, PaidOrder, CompletedOrder, CancelledOrder {

    Order order();

    boolean cancellable();

    OrderStatus status();

    default Order saveTransition(OrderStatus newStatus) {
        order().setStatus(newStatus);
        order().setUpdatedAt(java.time.Instant.now());
        return order();
    }
}
