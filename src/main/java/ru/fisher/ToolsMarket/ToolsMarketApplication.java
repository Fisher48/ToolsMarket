package ru.fisher.ToolsMarket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableJpaRepositories
public class ToolsMarketApplication {

	public static void main(String[] args) {
		SpringApplication.run(ToolsMarketApplication.class, args);
	}

}
