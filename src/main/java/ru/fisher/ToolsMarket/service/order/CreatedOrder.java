package ru.fisher.ToolsMarket.service.order;

import ru.fisher.ToolsMarket.exceptions.InvalidStatusTransitionException;
import ru.fisher.ToolsMarket.models.Order;
import ru.fisher.ToolsMarket.models.OrderStatus;

public record CreatedOrder(Order order) implements OrderState {

    public CreatedOrder {
        if (order == null) {
            throw new IllegalArgumentException("Заказ не может быть null");
        }
    }

    @Override
    public boolean cancellable() {
        return true;
    }

    @Override
    public OrderState moveTo(OrderStatus target) {
        return switch (target) {
            case PROCESSING -> new ProcessingOrder(saveTransition(OrderStatus.PROCESSING));
            case PAID       -> new PaidOrder(saveTransition(OrderStatus.PAID));
            case COMPLETED  -> new CompletedOrder(saveTransition(OrderStatus.COMPLETED));
            case CANCELLED  -> cancel();
            default -> throw new InvalidStatusTransitionException(
                    OrderStatus.CREATED.name(), target.name());
        };
    }

    @Override
    public OrderState cancel() {
        return new CancelledOrder(saveTransition(OrderStatus.CANCELLED));
    }
}