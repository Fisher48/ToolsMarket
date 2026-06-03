package ru.fisher.ToolsMarket.service;

import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Component
public class FoxweldParser implements ProductPageParser {

    private static final String BASE_URL = "https://foxweld.ru";

    @Override
    public boolean supports(String url) {
        return url.contains("foxweld.ru");
    }

    @Override
    public ParsedProductData parse(String url) throws Exception {
        Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .timeout(15000)
                .ignoreHttpErrors(true)
                .ignoreContentType(true)
                .get();

        ParsedProductData data = new ParsedProductData();

        // 1. Название
        Element nameEl = doc.selectFirst("h1.product_title, h1");
        if (nameEl != null) data.setName(nameEl.text().trim());

        data.setShortDescription(data.getName());

        // 2. Артикул
        Element skuEl = doc.selectFirst(".sku, [itemprop=sku]");
        if (skuEl != null) data.setSku(skuEl.text().replaceAll("[^a-zA-Z0-9\\-]", "").trim());

        // 3. Цена из .price-val b
        Element priceVal = doc.selectFirst(".price-val b");
        if (priceVal != null) {
            String priceStr = priceVal.text().replaceAll("[^\\d]", "");
            if (!priceStr.isEmpty()) {
                data.setPrice(new BigDecimal(priceStr));
            }
        }
        log.info("Цена: {}", data.getPrice());

        // 4. Описание из .element-text[itemprop=description] — берём ВСЁ как есть
        String descriptionHtml = null;
        Element fullDesc = doc.selectFirst(".element-text[itemprop=description]");
        if (fullDesc != null) {
            // Берём HTML напрямую, удаляя только лишние пробелы и &nbsp;
            descriptionHtml = fullDesc.html()
                    .replace("&nbsp;", " ")
                    .replaceAll("\\s+", " ")
                    .trim();

            log.info("Описание (длина): {}", descriptionHtml.length());
            log.info("Описание (первые 500 символов): {}",
                    descriptionHtml.substring(0, Math.min(500, descriptionHtml.length())));
        }

        if (descriptionHtml == null || descriptionHtml.isEmpty()) {
            descriptionHtml = "<p>Описание временно недоступно</p>";
        }
        data.setDescription(descriptionHtml);

        if (descriptionHtml == null || descriptionHtml.isEmpty()) {
            descriptionHtml = "<p>Описание временно недоступно</p>";
        }
        data.setDescription(descriptionHtml);

        // 5. Изображения (полноразмерные из ссылок fancy)
        List<String> imageUrls = new ArrayList<>();

        // Основное изображение из .element-image a.fancy
        Element mainImgLink = doc.selectFirst(".element-image a.fancy");
        if (mainImgLink != null) {
            String href = mainImgLink.attr("href");
            if (!href.isEmpty()) {
                if (!href.startsWith("http")) href = BASE_URL + href;
                imageUrls.add(href);
                log.info("Основное изображение: {}", href);
            }
        }

        // Дополнительные изображения из .element-images .owl-carousel a.fancy
        Elements extraImgLinks = doc.select(".element-images .owl-carousel a.fancy");
        for (Element link : extraImgLinks) {
            String href = link.attr("href");
            if (!href.isEmpty()) {
                if (!href.startsWith("http")) href = BASE_URL + href;
                if (!imageUrls.contains(href)) {
                    imageUrls.add(href);
                }
            }
        }

        data.setImageUrls(imageUrls);
        log.info("Найдено изображений: {}", imageUrls.size());

        return data;
    }
}
