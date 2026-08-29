package ru.fisher.ToolsMarket.service.order;

import org.junit.jupiter.api.Test;
import ru.fisher.ToolsMarket.exceptions.InvalidStatusTransitionException;
import ru.fisher.ToolsMarket.exceptions.OrderFinalizedException;
import ru.fisher.ToolsMarket.models.Order;
import ru.fisher.ToolsMarket.models.OrderStatus;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderStateTest {

    private Order order(OrderStatus status) {
        return Order.builder()
                .status(status)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    // ===== OrderStateFactory =====

    @Test
    void factoryMapsEveryStatusToItsState() {
        assertThat(OrderStateFactory.of(order(OrderStatus.CREATED)))
                .isExactlyInstanceOf(CreatedOrder.class);
        assertThat(OrderStateFactory.of(order(OrderStatus.PROCESSING)))
                .isExactlyInstanceOf(ProcessingOrder.class);
        assertThat(OrderStateFactory.of(order(OrderStatus.PAID)))
                .isExactlyInstanceOf(PaidOrder.class);
        assertThat(OrderStateFactory.of(order(OrderStatus.COMPLETED)))
                .isExactlyInstanceOf(CompletedOrder.class);
        assertThat(OrderStateFactory.of(order(OrderStatus.CANCELLED)))
                .isExactlyInstanceOf(CancelledOrder.class);
    }

    @Test
    void factoryRejectsNullOrder() {
        assertThatThrownBy(() -> OrderStateFactory.of(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ===== CreatedOrder =====

    @Test
    void createdOrderMovesForward() {
        Order o = order(OrderStatus.CREATED);
        Instant before = o.getUpdatedAt();

        assertThat(CreatedOrder.class.isInstance(OrderStateFactory.of(o))).isTrue();
        OrderState arrived = OrderStateFactory.of(o).moveTo(OrderStatus.PROCESSING);

        assertThat(arrived).isExactlyInstanceOf(ProcessingOrder.class);
        assertThat(o.getStatus()).isEqualTo(OrderStatus.PROCESSING);
        assertThat(o.getUpdatedAt()).isAfter(before);
    }

    @Test
    void createdOrderCanJumpStraightToPaidAndCompleted() {
        Order paid = order(OrderStatus.CREATED);
        assertThat(OrderStateFactory.of(paid).moveTo(OrderStatus.PAID))
                .isExactlyInstanceOf(PaidOrder.class);
        assertThat(paid.getStatus()).isEqualTo(OrderStatus.PAID);

        Order completed = order(OrderStatus.CREATED);
        assertThat(OrderStateFactory.of(completed).moveTo(OrderStatus.COMPLETED))
                .isExactlyInstanceOf(CompletedOrder.class);
        assertThat(completed.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    void createdOrderCanBeCancelled() {
        Order o = order(OrderStatus.CREATED);
        assertThat(OrderStateFactory.of(o).cancel())
                .isExactlyInstanceOf(CancelledOrder.class);
        assertThat(o.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void createdOrderRejectsMovingToSameStatus() {
        assertThatThrownBy(() -> OrderStateFactory.of(order(OrderStatus.CREATED))
                .moveTo(OrderStatus.CREATED))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void createdOrderIsCancellable() {
        assertThat(OrderStateFactory.of(order(OrderStatus.CREATED)).cancellable())
                .isTrue();
    }

    // ===== ProcessingOrder =====

    @Test
    void processingOrderMovesToPaidCompletedOrCancelled() {
        Order o = order(OrderStatus.PROCESSING);

        assertThat(OrderStateFactory.of(o).moveTo(OrderStatus.PAID))
                .isExactlyInstanceOf(PaidOrder.class);
        assertThat(o.getStatus()).isEqualTo(OrderStatus.PAID);

        Order viaCompleted = order(OrderStatus.PROCESSING);
        assertThat(OrderStateFactory.of(viaCompleted).moveTo(OrderStatus.COMPLETED))
                .isExactlyInstanceOf(CompletedOrder.class);
        assertThat(viaCompleted.getStatus()).isEqualTo(OrderStatus.COMPLETED);

        Order cancelled = order(OrderStatus.PROCESSING);
        assertThat(OrderStateFactory.of(cancelled).cancel())
                .isExactlyInstanceOf(CancelledOrder.class);
        assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void processingOrderCannotGoBack() {
        assertThatThrownBy(() -> OrderStateFactory.of(order(OrderStatus.PROCESSING))
                .moveTo(OrderStatus.CREATED))
                .isInstanceOf(InvalidStatusTransitionException.class);
        assertThatThrownBy(() -> OrderStateFactory.of(order(OrderStatus.PROCESSING))
                .moveTo(OrderStatus.PROCESSING))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void processingOrderIsCancellable() {
        assertThat(OrderStateFactory.of(order(OrderStatus.PROCESSING)).cancellable())
                .isTrue();
    }

    // ===== PaidOrder =====

    @Test
    void paidOrderMovesToCompletedOrCancelled() {
        Order completed = order(OrderStatus.PAID);
        assertThat(OrderStateFactory.of(completed).moveTo(OrderStatus.COMPLETED))
                .isExactlyInstanceOf(CompletedOrder.class);
        assertThat(completed.getStatus()).isEqualTo(OrderStatus.COMPLETED);

        Order cancelled = order(OrderStatus.PAID);
        assertThat(OrderStateFactory.of(cancelled).cancel())
                .isExactlyInstanceOf(CancelledOrder.class);
        assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void paidOrderCannotGoBackOrRepeat() {
        for (OrderStatus target : new OrderStatus[]{OrderStatus.CREATED,
                OrderStatus.PROCESSING, OrderStatus.PAID}) {
            assertThatThrownBy(() -> OrderStateFactory.of(order(OrderStatus.PAID))
                    .moveTo(target))
                    .isInstanceOf(InvalidStatusTransitionException.class);
        }
    }

    @Test
    void paidOrderIsCancellable() {
        assertThat(OrderStateFactory.of(order(OrderStatus.PAID)).cancellable())
                .isTrue();
    }

    // ===== CompletedOrder (терминальное) =====

    @Test
    void completedOrderRejectsEveryTransition() {
        for (OrderStatus target : OrderStatus.values()) {
            assertThatThrownBy(() -> OrderStateFactory.of(order(OrderStatus.COMPLETED))
                    .moveTo(target))
                    .isInstanceOf(OrderFinalizedException.class)
                    .hasMessage("Невозможно изменить статус заказа: заказ уже завершен");
        }
    }

    @Test
    void completedOrderRejectsCancellation() {
        assertThatThrownBy(() -> OrderStateFactory.of(order(OrderStatus.COMPLETED))
                .cancel())
                .isInstanceOf(OrderFinalizedException.class)
                .hasMessage("Невозможно изменить статус заказа: заказ уже завершен");
    }

    @Test
    void completedOrderIsNotCancellable() {
        assertThat(OrderStateFactory.of(order(OrderStatus.COMPLETED)).cancellable())
                .isFalse();
    }

    @Test
    void completedOrderStatusRemainsUntouchedAfterFailedTransition() {
        Order o = order(OrderStatus.COMPLETED);
        assertThatThrownBy(() -> OrderStateFactory.of(o).moveTo(OrderStatus.CANCELLED))
                .isInstanceOf(OrderFinalizedException.class);
        assertThat(o.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    // ===== CancelledOrder (терминальное) =====

    @Test
    void cancelledOrderRejectsEveryTransition() {
        for (OrderStatus target : OrderStatus.values()) {
            assertThatThrownBy(() -> OrderStateFactory.of(order(OrderStatus.CANCELLED))
                    .moveTo(target))
                    .isInstanceOf(OrderFinalizedException.class)
                    .hasMessage("Невозможно изменить статус заказа: заказ уже отменен");
        }
    }

    @Test
    void cancelledOrderRejectsCancellation() {
        assertThatThrownBy(() -> OrderStateFactory.of(order(OrderStatus.CANCELLED))
                .cancel())
                .isInstanceOf(OrderFinalizedException.class)
                .hasMessage("Невозможно изменить статус заказа: заказ уже отменен");
    }

    @Test
    void cancelledOrderIsNotCancellable() {
        assertThat(OrderStateFactory.of(order(OrderStatus.CANCELLED)).cancellable())
                .isFalse();
    }
}