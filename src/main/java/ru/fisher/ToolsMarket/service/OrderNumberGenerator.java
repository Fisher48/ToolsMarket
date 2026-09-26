package ru.fisher.ToolsMarket.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Генератор номеров заказов.
 *
 * Формат номера: yyMMddHHmm (10 цифр) + userId % 10000 (4 цифры) +
 * значение последовательности % 10000 (4 цифры) = 18 цифр.
 *
 * Уникальность обеспечивает последовательность в базе (order_number_seq),
 * а не случайные цифры: два заказа одного пользователя в пределах одной минуты
 * раньше совпадали с вероятностью 1%, и клиент получал 500 на оформлении заказа
 * из-за срабатывания UNIQUE-ограничения на order_number.
 *
 * Дата форматируется строкой намеренно: yyMMddHHmm не помещается в int
 * (например 2609261430 больше Integer.MAX_VALUE), поэтому собирать число нужно
 * только из строк, без промежуточного приведения к int.
 */
@Component
public class OrderNumberGenerator {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyMMddHHmm");
    private static final int PART_MODULO = 10_000;

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    @Autowired
    public OrderNumberGenerator(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, Clock.systemDefaultZone());
    }

    /**
     * Конструктор для тестов: позволяет зафиксировать время, чтобы проверять
     * номер целиком, а не по регулярному выражению.
     */
    OrderNumberGenerator(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    public Long next(Long userId) {
        String datePart = DATE_TIME.format(LocalDateTime.now(clock));
        String userPart = String.format("%04d", userPart(userId));
        String sequencePart = String.format("%04d", nextSequenceValue() % PART_MODULO);

        return Long.parseLong(datePart + userPart + sequencePart);
    }

    private long userPart(Long userId) {
        return userId == null ? 0 : Math.floorMod(userId, PART_MODULO);
    }

    private long nextSequenceValue() {
        Long value = jdbcTemplate.queryForObject("SELECT nextval('order_number_seq')", Long.class);
        if (value == null) {
            throw new IllegalStateException(
                    "Последовательность order_number_seq недоступна: проверьте миграцию V21");
        }
        return value;
    }
}
