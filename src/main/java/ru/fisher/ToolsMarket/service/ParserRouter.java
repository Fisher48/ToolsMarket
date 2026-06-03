package ru.fisher.ToolsMarket.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.fisher.ToolsMarket.util.ProductPageParser;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ParserRouter {

    private final List<ProductPageParser> parsers;

    /**
     * Выбирает подходящий парсер по URL. Если ни один не подходит, бросает исключение.
     */
    public ProductPageParser getParser(String url) {
        return parsers.stream()
                .filter(p -> p.supports(url))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Нет парсера для URL: " + url));
    }
}
