package ru.fisher.ToolsMarket.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import ru.fisher.ToolsMarket.dto.OrderCreatedEvent;
import ru.fisher.ToolsMarket.dto.OrderEmailPayload;
import ru.fisher.ToolsMarket.dto.SimpleOrderItemDto;
import ru.fisher.ToolsMarket.models.FailedEmail;
import ru.fisher.ToolsMarket.models.OrderStatus;
import ru.fisher.ToolsMarket.repository.FailedEmailRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    public final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;
    private final FailedEmailRepository failedEmailRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.mail.from}")
    public String from;

    @Value("${app.mail.admin}")
    private String adminEmail;

    @Retryable(
            retryFor = {
                    MailException.class,
                    MessagingException.class
            },
            maxAttempts = 3,
            backoff = @Backoff(delay = 3000, multiplier = 2)
    )
    public void sendOrderCreatedEmail(OrderCreatedEvent event) throws MessagingException {
        log.info("Sending order email for order {}", event.orderNumber());

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(from);
        helper.setTo(adminEmail);
        helper.setSubject("Новый заказ инструментов #" + event.orderNumber());

        // Контекст для Thymeleaf
        Context context = new Context();
        context.setVariable("order", event);
        context.setVariable("items", event.orderItems());
        context.setVariable("total", event.total());
        context.setVariable("formattedDate", LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));

        String htmlContent = templateEngine.process("email/order-created", context);
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
                simpleItems, // Используем упрощенный DTO
                event.total(),
                event.customerEmail()
        );
    }

    /**
     * Fallback после всех retry
     */
    @Recover
    public void recover(Exception ex, OrderCreatedEvent event) {
        log.error("Email sending FAILED after retries for order {}", event.orderNumber(), ex);
        OrderEmailPayload payload = toSimpleEmailPayload(event);
        failedEmailRepository.save(FailedEmail.from(payload, OrderStatus.CREATED, ex, objectMapper));
    }

}
