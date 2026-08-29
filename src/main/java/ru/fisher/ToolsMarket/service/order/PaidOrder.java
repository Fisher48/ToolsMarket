package ru.fisher.ToolsMarket.service.order;

import ru.fisher.ToolsMarket.exceptions.InvalidStatusTransitionException;
import ru.fisher.ToolsMarket.models.Order;
import ru.fisher.ToolsMarket.models.OrderStatus;

public record PaidOrder(Order order) implements OrderState {

    public PaidOrder {
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
            case COMPLETED  -> new CompletedOrder(saveTransition(OrderStatus.COMPLETED));
            case CANCELLED  -> cancel();
            default -> throw new InvalidStatusTransitionException(
                    OrderStatus.PAID.name(), target.name());
        };
    }

    @Override
    public OrderState cancel() {
        return new CancelledOrder(saveTransition(OrderStatus.CANCELLED));
    }
}