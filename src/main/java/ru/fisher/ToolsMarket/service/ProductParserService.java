package ru.fisher.ToolsMarket.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.fisher.ToolsMarket.dto.ParsedProductData;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductParserService {

    private final ParserRouter router;

    /**
     * Парсит товар по ссылке, автоматически выбирая нужный парсер.
     */
    public ParsedProductData parse(String url) throws Exception {
        return router.getParser(url).parse(url);
    }
}
