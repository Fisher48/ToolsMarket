package ru.fisher.ToolsMarket.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.spring6.SpringTemplateEngine;
import ru.fisher.ToolsMarket.dto.*;
import ru.fisher.ToolsMarket.dto.OrderDTO.OrderCreatedEvent;
import ru.fisher.ToolsMarket.dto.OrderDTO.OrderEmailPayload;
import ru.fisher.ToolsMarket.dto.UserDTO.UserRegistrationEvent;
import ru.fisher.ToolsMarket.models.FailedEmail;
import ru.fisher.ToolsMarket.models.OrderStatus;
import ru.fisher.ToolsMarket.repository.FailedEmailRepository;
import ru.fisher.ToolsMarket.service.email.*;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private static final int MAX_RETRIES = 2;

    public final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;
    private final FailedEmailRepository failedEmailRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.mail.from}")
    public String from;

    @Value("${app.mail.admin}")
    private String adminEmail;

    public void sendOrderCreatedEmail(OrderCreatedEvent event) {
        log.info("Sending order email for order {}", event.orderNumber());

        PendingEmail pending = EmailNotificationFactory.createOrderEmail(
                event, from, adminEmail, templateEngine);

        EmailNotificationState result = sendWithEmailState(pending);

        if (result instanceof FailedEmailState failed) {
            OrderEmailPayload payload = toSimpleEmailPayload(event);
            Exception ex = new RuntimeException(failed.errorMessage());
            failedEmailRepository.save(FailedEmail.from(payload, OrderStatus.CREATED, ex, objectMapper));
        }
    }

    public void sendUserRegistrationEmail(UserRegistrationEvent event) {
        log.info("Отправка сообщения о регистрации нового пользователя для админа: {}", event.email());

        PendingEmail pending = EmailNotificationFactory.createRegistrationEmail(
                event, from, adminEmail, templateEngine);

        EmailNotificationState result = sendWithEmailState(pending);

        if (result instanceof FailedEmailState failed) {
            log.error("Email sending FAILED after retries for registration: {}", event.email());
        }
    }

    private EmailNotificationState sendWithEmailState(PendingEmail pending) {
        EmailNotificationState result = pending.send(this::doSend);

        if (result instanceof FailedEmailState failed) {
            log.warn("First attempt failed, retrying ({})...", failed.errorMessage());
            for (int i = 0; i < MAX_RETRIES; i++) {
                result = failed.retry(this::doSend);
                if (result instanceof SentEmail) {
                    log.info("Retry {} succeeded", i + 1);
                    return result;
                }
                failed = (FailedEmailState) result;
                log.warn("Retry {} failed: {}", i + 1, failed.errorMessage());
            }
        }

        return result;
    }

    private void doSend(String recipient, String subject, String htmlContent) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(from);
        helper.setTo(recipient);
        helper.setSubject(subject);
        helper.setText(htmlContent, true);

        mailSender.send(message);
    }

    public OrderEmailPayload toSimpleEmailPayload(OrderCreatedEvent event) {
        List<SimpleOrderItemDto> simpleItems = event.orderItems().stream()
                .map(item -> new SimpleOrderItemDto(
                        item.getProductName(),
                        item.getQuantity(),
                        item.getUnitPrice(),
                        item.getSubtotal()
                ))
                .toList();

        return new OrderEmailPayload(
                event.orderId(),
                event.orderNumber(),
                simpleItems,
                event.total(),
                event.customerEmail(),
                event.note()
        );
    }
}
