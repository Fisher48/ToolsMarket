package ru.fisher.ToolsMarket.service.email;

public record SentEmail(
        String recipient,
        String subject
) implements EmailNotificationState {

}
