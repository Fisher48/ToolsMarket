package ru.fisher.ToolsMarket.exceptions;

// Невалидные данные заказа
public class OrderValidationException extends OrderException {
    public OrderValidationException(String field, String message) {
        super(String.format("Ошибка валидации поля '%s': %s", field, message));
    }
}
