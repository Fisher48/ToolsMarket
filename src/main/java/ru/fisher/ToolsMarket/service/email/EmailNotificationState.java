package ru.fisher.ToolsMarket.service.email;

public sealed interface EmailNotificationState
        permits PendingEmail, SentEmail, FailedEmailState {

    String recipient();

    String subject();
}
