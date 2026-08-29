package ru.fisher.ToolsMarket.service.order;

import ru.fisher.ToolsMarket.models.Order;
import ru.fisher.ToolsMarket.models.OrderStatus;

public final class OrderStateFactory {

    private OrderStateFactory() {
    }

    public static OrderState of(Order order) {
        if (order == null) {
            throw new IllegalArgumentException("Заказ не может быть null");
        }
        return switch (order.getStatus()) {
            case CREATED    -> new CreatedOrder(order);
            case PROCESSING -> new ProcessingOrder(order);
            case PAID       -> new PaidOrder(order);
            case COMPLETED  -> new CompletedOrder(order);
            case CANCELLED  -> new CancelledOrder(order);
        };
    }
}
