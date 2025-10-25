package ru.fisher.ToolsMarket.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.fisher.ToolsMarket.util.PriceFormatter;

@Configuration
public class ThymeleafConfig {

    @Bean
    public PriceFormatter priceFormatter() {
        return new PriceFormatter();
    }
}
