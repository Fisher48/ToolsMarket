package ru.fisher.ToolsMarket;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.session.MapSessionRepository;
import org.springframework.session.SessionRepository;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.HashMap;

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

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        postgres.start();

        TestPropertyValues.of(
                "spring.datasource.url=" + postgres.getJdbcUrl(),
                "spring.datasource.username=" + postgres.getUsername(),
                "spring.datasource.password=" + postgres.getPassword(),
                "spring.datasource.driver-class-name=org.postgresql.Driver",
                "spring.jpa.hibernate.ddl-auto=none",
                "spring.sql.init.mode=never",
                // Вариант B: Разрешаем конфликты
                "spring.session.jdbc.initialize-schema=always",
                "spring.flyway.out-of-order=true",
                "spring.flyway.enabled=true"
        ).applyTo(context.getEnvironment());
    }
}
