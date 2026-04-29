package ru.fisher.ToolsMarket.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import ru.fisher.ToolsMarket.dto.UserRegistrationEvent;
import ru.fisher.ToolsMarket.service.EmailService;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserRegistrationEmailListener {

    private final EmailService emailService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserRegistered(UserRegistrationEvent event) {
        try {
            emailService.sendUserRegistrationEmail(event);
            log.info("Сообщение о регистрации нового пользователя отправлено админу на почту: {}", event.email());
        } catch (Exception e) {
            log.error("Ошибка отправки сообщения о регистрации: {}", event.email(), e);
        }
    }
}
