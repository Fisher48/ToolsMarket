package ru.fisher.ToolsMarket.service.email;

public record FailedEmailState(
        String recipient,
        String subject,
        String htmlContent,
        String errorMessage,
        int failedAttempts
) implements EmailNotificationState {

    public EmailNotificationState retry(EmailSendAction action) {
        try {
            action.execute(recipient, subject, htmlContent);
            return new SentEmail(recipient, subject);
        } catch (Exception ex) {
            return new FailedEmailState(
                    recipient, subject, htmlContent,
                    ex.getMessage(), failedAttempts + 1
            );
        }
    }
}
