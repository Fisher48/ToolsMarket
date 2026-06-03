package ru.fisher.ToolsMarket.service;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;
import ru.fisher.ToolsMarket.dto.ParsedProductData;
import ru.fisher.ToolsMarket.util.ProductPageParser;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class BraitParser implements ProductPageParser {

    private static final String BASE_URL = "https://fdbrait.ru";

    @Override
    public boolean supports(String url) {
        return url.contains("fdbrait.ru");
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

        // 2. Артикул — ищем span.fw500 с текстом "Артикул", затем следующий span.fw
        Element skuLabel = doc.selectFirst("span.fw500:contains(Артикул)");
        if (skuLabel != null) {
            Element skuValue = skuLabel.parent().selectFirst("span.fw");
            if (skuValue != null) {
                data.setSku(skuValue.text().replace("—", "").trim());
                log.info("Артикул: {}", data.getSku());
            }
        }

        // 3. Цена — на этой странице цены нет
        // Цена не заполняется
        log.info("Цена не найдена (кнопка 'Где купить')");

        data.setShortDescription(data.getName());

        // 4. Описание — вкладка #tab-description
        Element descTab = doc.selectFirst("#tab-description");
        if (descTab != null) {
            data.setDescription(descTab.html());
            log.info("Описание (длина): {}", data.getDescription().length());
        } else {
            data.setDescription("<p>Описание временно недоступно</p>");
        }

        // 5. Изображения — из блока .slick_dop_img и из meta og:image
        List<String> imageUrls = new ArrayList<>();

        // Основное изображение из meta
        Element ogImage = doc.selectFirst("meta[property=og:image]");
        if (ogImage != null) {
            imageUrls.add(ogImage.attr("content"));
        }

        // Галерея из .slick_dop_img (полноразмерные из href)
        Elements galleryLinks = doc.select(".slick_dop_img a[href]");
        for (Element link : galleryLinks) {
            String href = link.attr("href");
            if (!href.isEmpty() && !href.contains("180x180")) { // только полноразмерные
                if (!href.startsWith("http")) href = BASE_URL + href;
                if (!imageUrls.contains(href)) imageUrls.add(href);
            }
        }

        data.setImageUrls(imageUrls);
        log.info("Найдено изображений: {}", imageUrls.size());

        return data;
    }
}
