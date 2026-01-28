package ru.fisher.ToolsMarket.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import ru.fisher.ToolsMarket.dto.OrderCreatedEvent;
import ru.fisher.ToolsMarket.service.EmailService;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEmailNotificationListener {

    private final EmailService emailService;

    @Async
    @EventListener
    public void onOrderCreated(final OrderCreatedEvent event) {
        try {
            emailService.sendOrderCreatedEmail(event);
        } catch (Exception e) {
            log.error("Failed to send order email for order {}", event.orderNumber(), e);
        }
    }


}
