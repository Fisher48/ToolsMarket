package ru.fisher.ToolsMarket;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest
@ContextConfiguration(initializers = PostgresTestConfig.class)
class ToolsMarketApplicationTests {

	@Test
	void contextLoads() {
	}

}

