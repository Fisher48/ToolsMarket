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

@Component
@Slf4j
public class HoztorgrParser implements ProductPageParser {

    private static final String BASE_URL = "https://hoztorgr.ru";

    @Override
    public boolean supports(String url) {
        return url.contains("hoztorgr.ru");
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

        // Примерные заглушки, нужно заменить на правильные селекторы
        Element nameEl = doc.selectFirst("h1.product-title, h1");
        if (nameEl != null) data.setName(nameEl.text().trim());

        Element skuEl = doc.selectFirst(".article, [itemprop=sku]");
        if (skuEl != null) data.setSku(skuEl.text().replaceAll("[^a-zA-Z0-9\\-]", "").trim());

        Element priceEl = doc.selectFirst(".price, [itemprop=price]");
        if (priceEl != null) {
            String priceStr = priceEl.text().replaceAll("[^\\d]", "");
            if (!priceStr.isEmpty()) data.setPrice(new BigDecimal(priceStr));
        }

        data.setShortDescription(data.getName());

        // 4. Описание (приоритет: #desc → .js-preview-description)
        String descriptionHtml = null;

        // 1) Сначала ищем полное описание в блоке #desc
        Element fullDesc = doc.selectFirst("#desc .js-detail-description, #desc [itemprop=description]");
        if (fullDesc != null) {
            descriptionHtml = fullDesc.html();
            log.info("Описание взято из #desc");
        }

        // 2) Если полного описания нет, берём из js-preview-description
        if (descriptionHtml == null || descriptionHtml.isEmpty()) {
            Element previewDesc = doc.selectFirst(".js-preview-description");
            if (previewDesc != null) {
                String text = previewDesc.text().trim();
                if (!text.isEmpty()) {
                    descriptionHtml = "<p>" + text + "</p>";
                    log.info("Описание взято из js-preview-description");
                }
            }
        }

        // 3) Заглушка
        if (descriptionHtml == null || descriptionHtml.isEmpty()) {
            descriptionHtml = "<p>Описание временно недоступно</p>";
            log.info("Описание не найдено, использована заглушка");
        }

        data.setDescription(descriptionHtml);

        // 5. Изображения
        List<String> imageUrls = new ArrayList<>();

        // Сначала ищем link[rel=image_src]
        Element linkImage = doc.selectFirst("link[rel=image_src]");
        if (linkImage != null) {
            String href = linkImage.attr("href");
            if (!href.isEmpty()) {
                imageUrls.add(href);
                log.info("Изображение из image_src: {}", href);
            }
        }

        // Потом meta og:image
        if (imageUrls.isEmpty()) {
            Element ogImage = doc.selectFirst("meta[property=og:image]");
            if (ogImage != null) {
                String content = ogImage.attr("content");
                if (!content.isEmpty()) {
                    imageUrls.add(content);
                    log.info("Изображение из og:image: {}", content);
                }
            }
        }

        // Если ничего не нашли — ищем все img
        if (imageUrls.isEmpty()) {
            Elements allImgs = doc.select("img");
            for (Element img : allImgs) {
                String src = img.attr("data-src");
                if (src.isEmpty()) src = img.attr("src");
                if (src.isEmpty() || src.contains("icon") || src.contains("logo")) continue;
                if (!src.startsWith("http")) src = BASE_URL + src;
                if (!imageUrls.contains(src)) imageUrls.add(src);
            }
        }

        data.setImageUrls(imageUrls);
        log.info("Найдено изображений: {}", imageUrls.size());

        return data;
    }
}
