package ru.fisher.ToolsMarket.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Превращает пользовательский ввод из строки поиска в безопасный tsquery
 * для полнотекстового индекса product.search_vector.
 *
 * Правила:
 * - вход разбивается на слова по не-буквенно-не-цифровым символам;
 * - остаются только слова, начинающиеся с буквы или цифры (это отсекает
 *   спецсимволы синтаксиса tsquery: &, |, !, :, *, скобки, кавычки);
 * - каждое слово ищется как префикс ({слово}:*), поэтому «дрел» находит
 *   «дрель», а «дрель» — «дрель»;
 * - слова объединяются оператором AND: все слова запроса должны найтись.
 *
 * Возвращает пустую строку, если ввод не содержит ни одного подходящего слова —
 * вызывающий код трактует это как «ничего не найдено», а не как «найди всё».
 */
public final class SearchQueryBuilder {

    private static final Pattern SEPARATORS = Pattern.compile("[^\\p{L}\\p{N}]+");
    // слово начинается с буквы или цифры: это отсекает спецсимволы синтаксиса tsquery
    // (&, |, !, :, *, скобки, кавычки) и оставляет обычные слова вроде «3квт»
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N}]*");

    private SearchQueryBuilder() {
    }

    /**
     * @return tsquery вида "дрел:* & 3квт:*", либо пустая строка
     */
    public static String toTsQuery(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }

        List<String> words = new ArrayList<>();
        for (String candidate : SEPARATORS.split(input.toLowerCase())) {
            String word = WORD.matcher(candidate).results()
                    .map(result -> result.group())
                    .findFirst()
                    .orElse(null);
            if (word != null && !words.contains(word)) {
                words.add(word);
            }
        }

        if (words.isEmpty()) {
            return "";
        }

        return String.join(" & ", words.stream().map(word -> word + ":*").toList());
    }
}
