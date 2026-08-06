package ru.fisher.ToolsMarket.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import ru.fisher.ToolsMarket.service.CategoryService;
import ru.fisher.ToolsMarket.service.ProductService;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

@RestController
@RequiredArgsConstructor
@Slf4j
public class SitemapController {

    private final ProductService productService;
    private final CategoryService categoryService;

    @Value("${app.base.url}")
    private String baseUrl;

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ISO_DATE.withZone(ZoneId.systemDefault());

    /**
     * Прогрев кэша при старте приложения — первый запрос бота не будет ждать 5 секунд.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void warmUpCache() {
        log.info("Warming up sitemap cache...");
        try {
            sitemap();
        } catch (Exception e) {
            log.warn("Sitemap warm-up failed: {}", e.getMessage());
        }
    }

    @CacheEvict(value = "sitemap", allEntries = true)
    @Scheduled(fixedRate = 12, timeUnit = TimeUnit.HOURS)
    public void evictSitemapCache() {
        log.debug("Sitemap cache evicted");
    }

    @Cacheable(value = "sitemap", cacheManager = "cacheManager")
    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    @ResponseBody
    public String sitemap() {
        long start = System.currentTimeMillis();
        int urlCount = 0;

        StringBuilder xml = new StringBuilder(1024 * 1024); // буфер ~1 MB
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        // 1. Главная
        xml.append(url(baseUrl, "/", "1.0", "daily", Instant.now()));
        urlCount++;

        // Категории: row[0]=title, row[1]=createdAt
        for (Object[] row : categoryService.findAllForSitemap()) {
            String title = (String) row[0];
            Instant createdAt = (Instant) row[1];

            xml.append(url(
                    baseUrl,
                    "/category/" + encode(title),
                    "0.8",
                    "weekly",
                    createdAt
            ));
            urlCount++;
        }

        // Товары: row[0]=title, row[1]=createdAt, row[2]=updatedAt
        for (Object[] row : productService.findAllForSitemap()) {
            String title = (String) row[0];
            Instant createdAt = (Instant) row[1];
            Instant updatedAt = (Instant) row[2];

            xml.append(url(
                    baseUrl,
                    "/product/" + encode(title),
                    "0.9",
                    "weekly",
                    updatedAt != null ? updatedAt : createdAt
            ));
            urlCount++;
        }

        xml.append("</urlset>\n");

        long duration = System.currentTimeMillis() - start;
        log.info("Sitemap generated in {} ms, {} URLs, size ~{} KB",
                duration, urlCount, xml.length() / 1024);

        return xml.toString();
    }

    private String url(String base, String path, String priority,
                       String changefreq, Instant lastmod) {
        String lastmodStr = lastmod != null
                ? DATE_FORMATTER.format(lastmod)
                : DATE_FORMATTER.format(Instant.now());

        // Нормализация: убираем двойной слэш
        String loc = (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + path;

        return "  <url>\n" +
                "    <loc>" + escapeXml(loc) + "</loc>\n" +
                "    <lastmod>" + lastmodStr + "</lastmod>\n" +
                "    <changefreq>" + changefreq + "</changefreq>\n" +
                "    <priority>" + priority + "</priority>\n" +
                "  </url>\n";
    }

    private String encode(String value) {
        if (value == null) return "";
        return value;
    }

    private String escapeXml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}