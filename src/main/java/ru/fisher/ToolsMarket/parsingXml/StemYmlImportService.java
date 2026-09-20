package ru.fisher.ToolsMarket.parsingXml;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.fisher.ToolsMarket.models.*;
import ru.fisher.ToolsMarket.repository.AttributeRepository;
import ru.fisher.ToolsMarket.repository.ProductAttributeValueRepository;
import ru.fisher.ToolsMarket.repository.ProductImageRepository;
import ru.fisher.ToolsMarket.repository.ProductRepository;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StemYmlImportService {

    private final YmlCategoryImporter categoryImporter;
    private final YmlOfferImporter offerImporter;
    private final ProductRepository productRepository;
    private final ProductAttributeValueRepository valueRepository;
    private final ProductImageRepository imageRepository;
    private final AttributeRepository attributeRepository;

    private static final int BATCH_SIZE = 200;

    @Transactional
    public ImportResult importFromUrl(String url) throws Exception {
        return runImport(url, false);
    }

    @Transactional(readOnly = true)
    public ImportResult previewFromUrl(String url) throws Exception {
        return runImport(url, true);
    }

    private ImportResult runImport(String url, boolean dryRun) throws Exception {
        log.info("{} из {}", dryRun ? "Предпросмотр (dry-run)" : "Импорт", url);

        long startTime = System.currentTimeMillis();

        // 1. Скачиваем XML один раз
        byte[] xmlData;
        try (InputStream is = new URL(url).openStream()) {
            xmlData = is.readAllBytes();
        }
        log.info("XML скачан: {} КБ за {} мс", xmlData.length / 1024,
                System.currentTimeMillis() - startTime);

        // 2. Импорт категорий
        Map<String, Category> categoryByXmlId;
        try (InputStream is = new ByteArrayInputStream(xmlData)) {
            categoryByXmlId = categoryImporter.importCategories(createReader(is), dryRun);
        }
        log.info("Категорий импортировано: {}", categoryByXmlId.size());

        // 3. Лёгкий проход по офферам — собираем только SKU,
        //  чтобы одним запросом предзагрузить существующие товары
        //  и не делать findBySku на каждый оффер (устраняет N+1).
        //  Сначала детектируем коллизии vendorCode — нужно для корректного
        //  резолва SKU (vendorCode может встречаться у > 1 оффера, см. stem),
        //  иначе два РАЗНЫХ товара сольются в один.
        Set<String> collidingVendorCodes;
        try (InputStream is = new ByteArrayInputStream(xmlData)) {
            XMLStreamReader reader = createReader(is);
            skipToOffers(reader);
            collidingVendorCodes = offerImporter.collectVendorCodeCounts(reader).entrySet().stream()
                    .filter(e -> e.getValue() > 1)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());
        }
        log.info("Коллизий vendorCode (встречаются у >1 оффера): {}", collidingVendorCodes.size());

        Set<String> skus;
        try (InputStream is = new ByteArrayInputStream(xmlData)) {
            XMLStreamReader reader = createReader(is);
            skipToOffers(reader);
            skus = offerImporter.collectSkus(reader, collidingVendorCodes);
        }
        log.info("Уникальных SKU в фиде: {}", skus.size());

        // Связи (картинки, значения атрибутов + атрибуты) предзагружаем
        // одним запросом, чтобы не делать кучу ленивых SELECT в YmlOfferImporter.
        Map<String, Product> productsBySku = skus.isEmpty()
                ? new HashMap<>()
                : productRepository.findAllBySkusWithDetails(skus).stream()
                .collect(Collectors.toMap(Product::getSku, p -> p));
        log.info("Предзагружено существующих товаров: {}", productsBySku.size());

        // 4. Создаём контекст.
        // Кэш значений атрибутов наполняем заранее
        // значениями предзагруженных товаров — это убирает точечный
        // findByProductIdAndAttributeId на каждый (товар, параметр).
        Map<String, ProductAttributeValue> valueCache = new HashMap<>();
        for (Product p : productsBySku.values()) {
            if (p.getAttributeValues() == null) continue;
            for (ProductAttributeValue pav : p.getAttributeValues()) {
                if (pav.getAttribute() != null) {
                    valueCache.put(p.getSku() + "_" + pav.getAttribute().getName(), pav);
                }
            }
        }

        // Кэш атрибутов строим терпимо к дублям: исторически в БД могли
        // попасть несколько атрибутов с одним (category_id, name) —
        // строгий Collectors.toMap падал с IllegalStateException
        // "Duplicate key <categoryId>_<name>". Оставляем один (минимальный id),
        // остальные логируем для последующей вычистки.
        Map<String, Attribute> attributeCache = attributeRepository.findAll().stream()
                .collect(Collectors.groupingBy(
                        a -> a.getCategory().getId() + "_" + a.getName(),
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                list -> {
                                    if (list.size() > 1) {
                                        list.sort(Comparator.comparing(Attribute::getId));
                                        log.warn("Дубликаты атрибутов: {} шт. по ключу '{}_{}' — оставлен id={}",
                                                list.size(), list.get(0).getCategory().getId(),
                                                list.get(0).getName(), list.get(0).getId());
                                    }
                                    return list.get(0);
                                }
                        )
                ));

        ImportContext ctx = new ImportContext(
                categoryByXmlId,
                productsBySku,
                attributeCache,
                valueCache
        );
        ctx.setCollidingVendorCodes(collidingVendorCodes);

        // 5. Полный импорт офферов
        try (InputStream is = new ByteArrayInputStream(xmlData)) {
            XMLStreamReader reader = createReader(is);
            skipToOffers(reader);
            offerImporter.importOffers(reader, ctx, dryRun);
        }

        // 6. Сохраняем всё батчами, чтобы не отправлять один гигантский
        //  saveAll по всем товарам/значениям атрибутов разом.
        //  Примечание: это снижает размер отдельных запросов к БД,
        //  но не разбивает импорт на отдельные транзакции — вся операция
        //  по-прежнему в одной @Transactional. Для очень больших фидов
        //  (десятки тысяч офферов) стоит рассмотреть разбиение на
        //  отдельные транзакции с периодическим entityManager.flush()/clear().
        if (!dryRun) {
            if (!ctx.getProductsToSave().isEmpty()) {
                saveInBatches(productRepository, ctx.getProductsToSave(), BATCH_SIZE);
            }
            if (!ctx.getValuesToSave().isEmpty()) {
                saveInBatches(valueRepository, ctx.getValuesToSave(), BATCH_SIZE);
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;

        // 7. Статистика — через явный трекинг новых SKU, а не через
        //  сравнение createdAt == updatedAt (ненадёжно из-за разных
        //  вызовов Instant.now() в рамках одного createOrUpdateProduct).
        int newProducts = ctx.getNewSkus().size();
        int updatedProducts = ctx.getProductsToSave().size() - newProducts;

        if (dryRun) {
            log.info("Предпросмотр завершен: всего={}, новых={}, обновлено={}, категорий={}, время={} с (сохранение пропущено)",
                    ctx.getProductsToSave().size(), newProducts, updatedProducts,
                    categoryByXmlId.size(), elapsed / 1000);
        } else {
            log.info("Импорт завершен: всего={}, новых={}, обновлено={}, категорий={}, время={} с",
                    ctx.getProductsToSave().size(), newProducts, updatedProducts,
                    categoryByXmlId.size(), elapsed / 1000);
        }

        return new ImportResult(true, categoryByXmlId.size(),
                ctx.getProductsToSave().size(), newProducts, updatedProducts,
                ctx.getChanges(), dryRun, null);
    }

    private void skipToOffers(XMLStreamReader reader) throws Exception {
        while (reader.hasNext()) {
            reader.next();
            if (reader.isStartElement() && reader.getLocalName().equals("offers")) break;
        }
    }

    private <T> void saveInBatches(JpaRepository<T, ?> repository, Collection<T> items, int batchSize) {
        List<T> list = new ArrayList<>(items);
        for (int i = 0; i < list.size(); i += batchSize) {
            List<T> batch = list.subList(i, Math.min(i + batchSize, list.size()));
            repository.saveAll(batch);
        }
    }

    private XMLStreamReader createReader(InputStream is) throws Exception {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        return factory.createXMLStreamReader(is);
    }

    // ---- DTO результата ----
    public static class ImportResult {
        private final boolean success;
        private final int categoriesImported;
        private final int offersImported;
        private final int newProducts;
        private final int updatedProducts;
        private final List<ProductChangeSummary> changes;
        private final String error;
        private final boolean dryRun;

        public ImportResult(boolean success,
                            int categoriesImported,
                            int offersImported,
                            int newProducts,
                            int updatedProducts,
                            List<ProductChangeSummary> changes,
                            boolean dryRun,
                            String error) {
            this.success = success;
            this.categoriesImported = categoriesImported;
            this.offersImported = offersImported;
            this.newProducts = newProducts;
            this.updatedProducts = updatedProducts;
            this.changes = changes;
            this.dryRun = dryRun;
            this.error = error;
        }

        public boolean isSuccess() { return success; }
        public int getCategoriesImported() { return categoriesImported; }
        public int getOffersImported() { return offersImported; }
        public int getNewProducts() { return newProducts; }
        public int getUpdatedProducts() { return updatedProducts; }
        public List<ProductChangeSummary> getChanges() { return changes; }
        public boolean isDryRun() { return dryRun; }
        public String getError() { return error; }
    }
}