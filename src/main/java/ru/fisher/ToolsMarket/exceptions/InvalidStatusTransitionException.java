package ru.fisher.ToolsMarket.exceptions;

// Некорректный переход статуса
public class InvalidStatusTransitionException extends OrderStatusException {
    public InvalidStatusTransitionException(String currentStatus, String targetStatus) {
        super(currentStatus, targetStatus,
                String.format("Некорректный переход статуса: %s → %s",
                        currentStatus, targetStatus));
    }
}