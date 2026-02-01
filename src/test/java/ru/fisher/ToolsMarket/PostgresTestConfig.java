package ru.fisher.ToolsMarket;

import org.jetbrains.annotations.NotNull;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.session.MapSessionRepository;
import org.springframework.session.SessionRepository;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.HashMap;
import java.util.Properties;

import static org.mockito.Mockito.mock;

@TestConfiguration
public class PostgresTestConfig implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("testdb")
                    .withUsername("test")
                    .withPassword("test");

    @Bean
    public SessionRepository<?> sessionRepository() {
        return new MapSessionRepository(new HashMap<>());
    }

    @Bean
    @Primary  // Основной бин для приложения
    public JavaMailSender testJavaMailSender() {
        return mock(JavaMailSender.class);
    }

    @Bean(name = "actuatorMailSender")  // Отдельный бин для Actuator
    public JavaMailSender actuatorMailSender() {
        // Создаем реальный (но фиктивный) JavaMailSender для Actuator
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost("localhost");
        mailSender.setPort(1025);

        Properties props = new Properties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "false");
        props.put("mail.smtp.starttls.enable", "false");
        props.put("mail.debug", "false");

        mailSender.setJavaMailProperties(props);
        return mailSender;
    }

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

                // Mail
                "spring.mail.host=localhost",
                "spring.mail.port=1025",
                "spring.mail.default-encoding=UTF-8",
                "spring.mail.properties.mail.smtp.auth=false",
                "spring.mail.properties.mail.smtp.starttls.enable=false",

                // App config
                "app.mail.from=test@example.com",
                "app.mail.admin=admin@example.com",

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
    }
}
