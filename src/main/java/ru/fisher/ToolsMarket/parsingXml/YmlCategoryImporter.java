package ru.fisher.ToolsMarket.parsingXml;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.fisher.ToolsMarket.models.Category;
import ru.fisher.ToolsMarket.repository.CategoryRepository;

import javax.xml.stream.XMLStreamReader;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class YmlCategoryImporter {

    private final CategoryRepository categoryRepository;

    @Transactional
    public Map<String, Category> importCategories(XMLStreamReader reader) throws Exception {

        Map<String, Category> categoryByXmlId = new HashMap<>();
        Map<String, String> parentRelations = new HashMap<>();
        Map<String, String> categoryNamesByXmlId = new HashMap<>();

        // Временно храним созданные/найденные категории без родителей
        Map<String, Category> tempCategories = new HashMap<>();

        // ПЕРВЫЙ ПРОХОД: собираем все названия категорий
        while (reader.hasNext()) {
            reader.next();

            if (reader.isStartElement() && reader.getLocalName().equals("category")) {

                String xmlId = reader.getAttributeValue(null, "id");
                String parentXmlId = reader.getAttributeValue(null, "parentId");
                String name = reader.getElementText();

                categoryNamesByXmlId.put(xmlId, name);

                if (parentXmlId != null) {
                    parentRelations.put(xmlId, parentXmlId);
                }

                // Сразу создаем/находим категорию (без parent)
                Category category = findOrCreateCategory(name);
                tempCategories.put(xmlId, category);
                log.debug("Категория: {} (title: {})", name, category.getTitle());
            }
        }

        // ВТОРОЙ ПРОХОД: устанавливаем родительские связи
        for (Map.Entry<String, String> relation : parentRelations.entrySet()) {
            String childXmlId = relation.getKey();
            String parentXmlId = relation.getValue();

            Category child = tempCategories.get(childXmlId);
            Category parent = tempCategories.get(parentXmlId);

            if (child != null && parent != null) {
                // Проверяем, нужно ли обновлять родителя
                if (child.getParent() == null || !child.getParent().getId().equals(parent.getId())) {
                    child.setParent(parent);
                    categoryRepository.save(child);
                    log.debug("Установлена связь: {} -> {}",
                            child.getName(), parent.getName());
                }
            } else {
                log.warn("Не найдена категория для связи: child={}, parent={}",
                        childXmlId, parentXmlId);
            }
        }

        // Заполняем результат
        for (Map.Entry<String, Category> entry : tempCategories.entrySet()) {
            categoryByXmlId.put(entry.getKey(), entry.getValue());
        }

        log.info("Импортировано категорий: {}", categoryByXmlId.size());
        return categoryByXmlId;
    }

    /**
     * Поиск или создание категории
     */
    private Category findOrCreateCategory(String name) {
        return findOrCreateCategory(name, null);
    }

    /**
     * Поиск или создание категории с родителем (используется для первого прохода)
     */
    private Category findOrCreateCategory(String name, Category parent) {
        String title = generateTitle(name);

        return categoryRepository.findByTitle(title)
                .map(existingCategory -> {
                    // Если категория существует, но name изменился - обновляем
                    if (!existingCategory.getName().equals(name)) {
                        existingCategory.setName(name);
                        return categoryRepository.save(existingCategory);
                    }
                    return existingCategory;
                })
                .orElseGet(() -> {
                    Category newCategory = Category.builder()
                            .name(name)
                            .title(title)
                            .createdAt(Instant.now())
                            .sortOrder(0)
                            .build();
                    return categoryRepository.save(newCategory);
                });
    }

    /**
     * Генерация URL-friendly title
     */
    private String generateTitle(String name) {
        if (name == null || name.isEmpty()) {
            return "category-" + System.currentTimeMillis();
        }

        // Транслитерация
        String title = transliterate(name);

        // Заменяем все не-буквенно-цифровые символы на подчеркивания
        title = title.replaceAll("[^a-zA-Z0-9\\s-]", "")
                .replaceAll("\\s+", "_")
                .replaceAll("-+", "_")
                .toLowerCase();

        // Убираем начальные и конечные подчеркивания
        title = title.replaceAll("^_+|_+$", "");

        // Убираем множественные подчеркивания
        title = title.replaceAll("_+", "_");

        if (title.length() > 200) {
            title = title.substring(0, 200);
        }

        if (title.endsWith("_")) {
            title = title.substring(0, title.length() - 1);
        }

        return title;
    }

    /**
     * Транслитерация (как в твоем JS)
     */
    private String transliterate(String text) {
        Map<Character, String> map = new HashMap<>();
        map.put('а', "a"); map.put('б', "b"); map.put('в', "v"); map.put('г', "g");
        map.put('д', "d"); map.put('е', "e"); map.put('ё', "yo"); map.put('ж', "zh");
        map.put('з', "z"); map.put('и', "i"); map.put('й', "y"); map.put('к', "k");
        map.put('л', "l"); map.put('м', "m"); map.put('н', "n"); map.put('о', "o");
        map.put('п', "p"); map.put('р', "r"); map.put('с', "s"); map.put('т', "t");
        map.put('у', "u"); map.put('ф', "f"); map.put('х', "kh"); map.put('ц', "ts");
        map.put('ч', "ch"); map.put('ш', "sh"); map.put('щ', "shch"); map.put('ъ', "");
        map.put('ы', "y"); map.put('ь', ""); map.put('э', "e"); map.put('ю', "yu");
        map.put('я', "ya");

        // Заглавные
        map.put('А', "A"); map.put('Б', "B"); map.put('В', "V"); map.put('Г', "G");
        map.put('Д', "D"); map.put('Е', "E"); map.put('Ё', "Yo"); map.put('Ж', "Zh");
        map.put('З', "Z"); map.put('И', "I"); map.put('Й', "Y"); map.put('К', "K");
        map.put('Л', "L"); map.put('М', "M"); map.put('Н', "N"); map.put('О', "O");
        map.put('П', "P"); map.put('Р', "R"); map.put('С', "S"); map.put('Т', "T");
        map.put('У', "U"); map.put('Ф', "F"); map.put('Х', "Kh"); map.put('Ц', "Ts");
        map.put('Ч', "Ch"); map.put('Ш', "Sh"); map.put('Щ', "Shch"); map.put('Ъ', "");
        map.put('Ы', "Y"); map.put('Ь', ""); map.put('Э', "E"); map.put('Ю', "Yu");
        map.put('Я', "Ya");

        StringBuilder result = new StringBuilder();
        for (char c : text.toCharArray()) {
            result.append(map.getOrDefault(c, String.valueOf(c)));
        }
        return result.toString();
    }
}

