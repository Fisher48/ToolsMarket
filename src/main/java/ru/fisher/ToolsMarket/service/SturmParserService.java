package ru.fisher.ToolsMarket.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;
import ru.fisher.ToolsMarket.dto.ParsedProductData;
import ru.fisher.ToolsMarket.util.ProductPageParser;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class SturmParserService implements ProductPageParser {

    private static final String BASE_URL = "https://sturmtools.ru";

    @Override
    public boolean supports(String url) {
        return url.contains("sturmtools.ru");
    }

    @Override
    public ParsedProductData parse(String url) throws Exception {
        // Подключаемся к странице, игнорируем ошибки SSL и ставим большой таймаут
        Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .timeout(15000)
                .ignoreHttpErrors(true)
                .ignoreContentType(true)
                .get();

        ParsedProductData data = new ParsedProductData();

        // 1. НАЗВАНИЕ (Тег h1)
        Element titleEl = doc.selectFirst("h1");
        if (titleEl != null) {
            data.setName(titleEl.text().trim());
        }

        // 2. АРТИКУЛ
        // Ищем в блоке <div class="font-size-14">Артикул: <b> PL5114SVE</b></div>
        Element skuEl = doc.selectFirst(".font-size-14 b");
        if (skuEl != null) {
            data.setSku(skuEl.text().trim());
        }

        // 3. ЦЕНА
        // Ищем <div class="catalog-pay__price"> 53 040 р. </div>
        Element priceEl = doc.selectFirst(".catalog-pay__price");
        if (priceEl != null) {
            try {
                // Убираем пробелы, неразрывные пробелы и буквы "р."
                String priceStr = priceEl.text().replaceAll("[^0-9]", "");
                if (!priceStr.isEmpty()) {
                    data.setPrice(new BigDecimal(priceStr));
                }
            } catch (Exception ignored) {
                // Если не удалось распарсить цену, оставляем null (заполнится нулем в контроллере)
            }
        }

        // 4. ОПИСАНИЕ
        // Ищем внутри <div class="collapse-panel__collapse collapse" id="description">
        Element descEl = doc.selectFirst("#description .collapse-panel__wrapper");
        if (descEl != null) {
            data.setDescription(descEl.html());
        } else {
            data.setDescription("<h2>" + data.getName() + "</h2>");
        }

        // 5. КАРТИНКИ (ГАЛЕРЕЯ)
        List<String> imageUrls = new ArrayList<>();

        // По коду Sturm: картинки лежат в data-src у слайдов с классом catalog-slider__slide
        Elements slideElements = doc.select(".catalog-slider__wrapper.lightgallery .catalog-slider__slide");

        for (Element slide : slideElements) {
            String src = slide.attr("data-src");

            // Если data-src пустой, пробуем найти тег img внутри слайда
            if (src == null || src.isEmpty()) {
                Element img = slide.selectFirst("img");
                if (img != null) {
                    src = img.attr("src");
                }
            }

            // Добавляем картинку, если нашли ссылку
            if (src != null && !src.isEmpty() && !src.contains("data:image/gif")) { // Игнорируем заглушки lazyload
                // Если ссылка относительная (/upload/iblock/...), добавляем домен
                if (!src.startsWith("http")) {
                    src = BASE_URL + src;
                }

                // Проверяем, чтобы не добавить дубликаты
                if (!imageUrls.contains(src)) {
                    imageUrls.add(src);
                }
            }
        }

        data.setImageUrls(imageUrls);

        return data;
    }
}
