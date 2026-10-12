package ru.fisher.ToolsMarket.util;

import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Единая точка percent-кодирования сегментов URL.
 * Используется sitemap'ом и шаблонами, чтобы кириллица в title товара
 * не попадала в URL в «сыром» виде: наивные клиенты обрезают путь
 * на первом non-ASCII байте и получают 404.
 */
@Component
public class PathSegmentEncoder {

    public String encode(String value) {
        if (value == null) {
            return "";
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
