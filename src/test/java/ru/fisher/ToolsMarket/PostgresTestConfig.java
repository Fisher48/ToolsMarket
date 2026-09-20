package ru.fisher.ToolsMarket;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mail.javamail.JavaMailSender;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.Properties;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@TestConfiguration
public class PostgresTestConfig implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("testdb")
                    .withUsername("test")
                    .withPassword("test");

    @Override
    public void initialize(@NotNull ConfigurableApplicationContext context) {
        if (!postgres.isRunning()) {
            postgres.start();
        }

        TestPropertyValues.of(
                // PostgreSQL из Testcontainers
                "spring.datasource.url=" + postgres.getJdbcUrl(),
                "spring.datasource.username=" + postgres.getUsername(),
                "spring.datasource.password=" + postgres.getPassword(),
                "spring.datasource.driver-class-name=org.postgresql.Driver",

                // Каждый @SpringBootTest-контекст держит свой пул Hikari (по умолчанию
                // до 10 соединений). При большом числе одновременно живых контекстов
                // общий Postgres (max_connections=100) переполняется — "too many clients".
                "spring.datasource.hikari.maximum-pool-size=2",

                // Flyway
                "spring.flyway.enabled=true",
                "spring.flyway.locations=classpath:db/migration",
                "spring.flyway.out-of-order=true",
                "spring.flyway.validate-on-migrate=false",

                // JPA
                "spring.jpa.hibernate.ddl-auto=none",
                "spring.jpa.show-sql=false",
                "spring.jpa.properties.hibernate.format_sql=false",
                "spring.jpa.open-in-view=false",

                // Spring Data
                "spring.data.jpa.repositories.enabled=true",
                "spring.data.jdbc.repositories.enabled=false",

                // SQL init
                "spring.sql.init.mode=never",

                // Spring Session
                "spring.session.jdbc.initialize-schema=never",

                // Mail - отключаем автоконфигурацию и работаем с моком (см. ниже)
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration",

                // App config
                "app.mail.from=test@example.com",
                "app.mail.admin=admin@example.com",
                "app.security.login-attempts.max-without-captcha=3",

                // Actuator - ОТКЛЮЧАЕМ
                "management.endpoints.enabled-by-default=false",
                "management.endpoint.health.enabled=false",
                "management.health.enabled=false",
                "management.health.mail.enabled=false",

                // Other
                "spring.main.allow-bean-definition-overriding=true",
                "spring.main.lazy-initialization=true",
                "spring.jmx.enabled=false",
                "spring.shell.interactive.enabled=false",
                "server.port=0"
        ).applyTo(context.getEnvironment());

        ConfigurableListableBeanFactory beanFactory = context.getBeanFactory();
        JavaMailSender mailSender = mock(JavaMailSender.class);
        when(mailSender.createMimeMessage())
                .thenReturn(new MimeMessage(Session.getDefaultInstance(new Properties())));
        beanFactory.registerSingleton("mailSender", mailSender);
    }
}
