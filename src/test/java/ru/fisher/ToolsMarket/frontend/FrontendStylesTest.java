package ru.fisher.ToolsMarket.frontend;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Статическая проверка фронтенда (без Spring и Docker):
 * 1) каждый шаблон-страница подключает /css/site.css;
 * 2) никто не ссылается на удалённые ресурсы;
 * 3) все кастомные CSS-классы определены (site.css, admin-sidebar.css или инлайн-&lt;style&gt; файла).
 */
class FrontendStylesTest {

    private static final Path TEMPLATES = Paths.get("src/main/resources/templates");
    private static final Path CSS_DIR = Paths.get("src/main/resources/static/css");
    private static final Path SITE_CSS = CSS_DIR.resolve("site.css");
    private static final Path ADMIN_CSS = CSS_DIR.resolve("admin-sidebar.css");

    private static final List<String> DELETED_RESOURCES = List.of(
            "css/auth.css",
            "fragments/layout",
            "fragments/discount-fragments",
            "fragments/order-fragments",
            "indexCategories"
    );

    private static final Pattern CSS_CLASS = Pattern.compile("\\.([A-Za-z_][A-Za-z0-9_-]*)");
    private static final Pattern STYLE_BLOCK = Pattern.compile("(?is)<style[^>]*>(.*?)</style>");
    private static final Pattern CLASS_ATTR =
            Pattern.compile("(?:th:classappend|th:class|\\bclass)\\s*=\\s*([\"'])(.*?)\\1");

    // Thymeleaf-выражения и строковые литералы внутри class-атрибутов
    private static final Pattern THYMELEAF_EXPRESSION =
            Pattern.compile("(?:\\$\\{|\\*\\{|#\\{|~\\{)[^}]*\\}");

    private static final Set<String> THYMELEAF_WORDS = Set.of(
            "and", "or", "not", "eq", "ne", "lt", "gt", "le", "ge",
            "true", "false", "null", "this"
    );

    // Bootstrap / Bootstrap Icons / Font Awesome / Swiper / Leaflet / Quill / Alpine
    private static final List<String> FRAMEWORK_PREFIXES = List.of(
            "btn", "col", "row", "row-cols", "container", "g-", "gx-", "gy-", "m-", "mt-", "mb-",
            "ms-", "me-", "mx-", "my-", "p-", "pt-", "pb-", "ps-", "pe-", "px-", "py-", "d-",
            "flex-", "justify-content-", "align-items-", "align-self-", "align-content-", "align-",
            "order-", "text-", "bg-", "border", "rounded", "shadow", "position-", "top-", "start-",
            "end-", "float-", "fw-", "fs-", "fst-", "lh-", "w-", "h-", "mw-", "mh-", "opacity-",
            "overflow-", "ratio", "table", "form-", "input-group", "navbar", "nav", "dropdown",
            "card", "accordion", "alert", "badge", "breadcrumb", "list-group", "page-item",
            "page-link", "pagination", "progress", "spinner", "toast", "modal", "tooltip",
            "popover", "carousel", "collapse", "offcanvas", "placeholder", "display-", "img-",
            "close", "active", "disabled", "show", "fade", "visible", "invisible", "sticky",
            "fixed-", "gap-", "object-fit-", "word-", "z-", "user-select-", "pointer-events-",
            "stretched-link", "visually-hidden", "lead", "small", "mark", "initialism",
            "blockquote", "figure", "hr", "vr", "bi", "swiper", "leaflet", "x-", "fa-", "ql-",
            "link-", "list-", "translate-", "vh-", "min-vh-", "font-", "vstack", "hstack", "badge-",
            "tab-", "invalid-", "valid-", "was-", "needs-"
    );

    private static final Set<String> FRAMEWORK_EXACT = Set.of(
            "fas", "far", "fab", "fa-solid", "fa-regular", "fa-brands",
            "vstack", "hstack",
            "h1", "h2", "h3", "h4", "h5", "h6"
    );

    // Унаследованный класс-обёртка админки (.content pt-3) — стилей не требует.
    private static final Set<String> KNOWN_UNSTYLED = Set.of(
            "content",
            // Pre-existing (были без стилей ещё в HEAD) — не регрессии рефакторинга
            "tg-icon", "map-section",
            "unit-price", "item-total-price", "old-total-price", "current-total-price",
            "quantity-btn-minus", "quantity-btn-plus", "compact-cart-controls",
            "image-upload-item", "remove-new-image-btn", "remove-image-btn",
            "image-item", "image-order-input", "sort-order-display",
            "user-badge"
    );

    @Test
    void cssFilesArePresentAndNonEmpty() throws IOException {
        assertThat(Files.readString(SITE_CSS)).isNotBlank();
        assertThat(Files.readString(ADMIN_CSS)).isNotBlank();
    }

    @Test
    void siteCssLinkedOnAllStandalonePages() throws IOException {
        List<String> failures = new ArrayList<>();
        for (Path file : allTemplates()) {
            if (isFragment(file) || isEmail(file)) {
                continue;
            }
            String html = Files.readString(file);
            boolean hasSiteCss = html.contains("/css/site.css");
            boolean hasAdminCss = html.contains("/css/admin-sidebar.css");
            if (!hasSiteCss && !hasAdminCss) {
                failures.add(rel(file) + " не подключает /css/site.css или /css/admin-sidebar.css");
            }
        }
        assertThat(failures).as("страницы без site.css").isEmpty();
    }

    @Test
    void noReferencesToDeletedResources() throws IOException {
        List<String> failures = new ArrayList<>();
        for (Path file : allTemplates()) {
            String html = Files.readString(file);
            for (String resource : DELETED_RESOURCES) {
                if (html.contains(resource)) {
                    failures.add(rel(file) + " ссылается на удалённый ресурс: " + resource);
                }
            }
        }
        assertThat(failures).as("ссылки на удалённые ресурсы").isEmpty();
    }

    @Test
    void everyCustomCssClassIsDefined() throws IOException {
        Set<String> defined = new LinkedHashSet<>(extractCssClasses(Files.readString(SITE_CSS)));
        defined.addAll(extractCssClasses(Files.readString(ADMIN_CSS)));

        List<String> failures = new ArrayList<>();
        for (Path file : allTemplates()) {
            String html = Files.readString(file);

            Set<String> available = new LinkedHashSet<>(defined);
            Matcher styleMatcher = STYLE_BLOCK.matcher(html);
            while (styleMatcher.find()) {
                available.addAll(extractCssClasses(styleMatcher.group(1)));
            }

            for (String cls : extractUsedClasses(html)) {
                if (!available.contains(cls)) {
                    failures.add(rel(file) + ": не определён класс '" + cls + "'");
                }
            }
        }
        assertThat(failures).as("не определённые CSS-классы").isEmpty();
    }

    private static List<Path> allTemplates() throws IOException {
        try (Stream<Path> walk = Files.walk(TEMPLATES)) {
            return walk.filter(p -> p.toString().endsWith(".html")).sorted().toList();
        }
    }

    private static Set<String> extractCssClasses(String css) {
        String clean = css.replaceAll("(?s)/\\*.*?\\*/", " ")
                .replaceAll("url\\s*\\(\\s*(['\"]?)[^)]*\\1\\s*\\)", " ");
        Set<String> classes = new LinkedHashSet<>();
        Matcher m = CSS_CLASS.matcher(clean);
        while (m.find()) {
            classes.add(m.group(1));
        }
        return classes;
    }

    private static Set<String> extractUsedClasses(String html) {
        String clean = html.replaceAll("(?s)<!--.*?-->", " ");
        Set<String> used = new LinkedHashSet<>();
        Matcher m = CLASS_ATTR.matcher(clean);
        while (m.find()) {
            String value = THYMELEAF_EXPRESSION.matcher(m.group(2)).replaceAll(" ");
            value = value.replaceAll("'[^']*'", " ");
            value = value.replaceAll("\"[^\"]*\"", " ");
            for (String token : value.split("\\s+")) {
                token = token.trim();
                if (token.isEmpty()) {
                    continue;
                }
                if (!token.matches("[A-Za-z_][A-Za-z0-9_-]*") || isFrameworkOrDynamic(token)) {
                    continue;
                }
                used.add(token);
            }
        }
        return used;
    }

    private static boolean isFrameworkOrDynamic(String cls) {
        for (char c : cls.toCharArray()) {
            if (!Character.isLetterOrDigit(c) && c != '-' && c != '_') {
                return true;
            }
        }
        if (FRAMEWORK_EXACT.contains(cls) || KNOWN_UNSTYLED.contains(cls)
                || THYMELEAF_WORDS.contains(cls)) {
            return true;
        }
        for (String prefix : FRAMEWORK_PREFIXES) {
            if (cls.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isFragment(Path file) {
        return file.startsWith(TEMPLATES.resolve("fragments"));
    }

    private static boolean isEmail(Path file) {
        return file.startsWith(TEMPLATES.resolve("email"));
    }

    private static String rel(Path file) {
        return TEMPLATES.relativize(file).toString().replace('\\', '/');
    }
}
