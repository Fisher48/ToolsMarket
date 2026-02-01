package ru.fisher.ToolsMarket.models;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ru.fisher.ToolsMarket.dto.OrderEmailPayload;

import java.time.Instant;

@Entity
@Table(name = "failed_emails")
@Getter
@NoArgsConstructor
public class FailedEmail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private OrderStatus orderStatus; // Статус заказа

    private String recipient; // Получатель

    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    private String errorMessage;

    private Instant failedAt;

    public static FailedEmail from(
            OrderEmailPayload payload,
            OrderStatus status,
            Exception ex,
            ObjectMapper mapper
    ) {
        FailedEmail f = new FailedEmail();
        f.orderStatus = status;
        f.recipient = payload.customerEmail();
        try {
            f.payloadJson = mapper.writeValueAsString(payload);
        } catch (Exception e) {
            f.payloadJson = "{}";
        }
        f.errorMessage = ex.getMessage();
        f.failedAt = Instant.now();
        return f;
    }
}
