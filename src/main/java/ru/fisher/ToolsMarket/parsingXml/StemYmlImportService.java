package ru.fisher.ToolsMarket.parsingXml;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.fisher.ToolsMarket.models.Attribute;
import ru.fisher.ToolsMarket.models.Category;
import ru.fisher.ToolsMarket.models.Product;
import ru.fisher.ToolsMarket.models.ProductAttributeValue;
import ru.fisher.ToolsMarket.repository.AttributeRepository;
import ru.fisher.ToolsMarket.repository.ProductAttributeValueRepository;
import ru.fisher.ToolsMarket.repository.ProductRepository;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.net.URL;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StemYmlImportService {

    private final YmlCategoryImporter categoryImporter;
    private final YmlOfferImporter offerImporter;
    private final ProductRepository productRepository;
    private final ProductAttributeValueRepository valueRepository;
    private final AttributeRepository attributeRepository;

    @Transactional
    public ImportResult importFromUrl(String url) throws Exception {

        log.info("Импорт из {}", url);

        Map<String, Category> categoryByXmlId;

        // -------- ЭТАП 1: категории --------
        try (InputStream is = new URL(url).openStream()) {
            XMLStreamReader reader = createReader(is);
            categoryByXmlId = categoryImporter.importCategories(reader);
        }

        // -------- ЭТАП 2: preload --------
        ImportContext ctx = prepareContext(categoryByXmlId);

        // -------- ЭТАП 3: товары --------
        try (InputStream is = new URL(url).openStream()) {
            XMLStreamReader reader = createReader(is);

            while (reader.hasNext()) {
                reader.next();
                if (reader.isStartElement()
                        && reader.getLocalName().equals("offers")) {
                    break;
                }
            }

            offerImporter.importOffers(reader, ctx);
        }

        // -------- ЭТАП 4: batch flush --------
        flush(ctx);

        log.info("Импорт завершен");

        return new ImportResult(true,
                categoryByXmlId.size(),
                ctx.getProductsToSave().size(),
                null);
    }

    private XMLStreamReader createReader(InputStream is) throws Exception {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        return factory.createXMLStreamReader(is);
    }

    private ImportContext prepareContext(Map<String, Category> categoryByXmlId) {

        Map<String, Product> productsBySku =
                productRepository.findAll()
                        .stream()
                        .collect(Collectors.toMap(
                                Product::getSku,
                                p -> p
                        ));

        Map<String, Attribute> attributeCache =
                attributeRepository.findAll()
                        .stream()
                        .collect(Collectors.toMap(
                                a -> a.getCategory().getId() + "_" + a.getName(),
                                a -> a
                        ));

        Map<String, ProductAttributeValue> valueCache =
                valueRepository.findAll()
                        .stream()
                        .collect(Collectors.toMap(
                                v -> v.getProduct().getId()
                                        + "_" + v.getAttribute().getId(),
                                v -> v
                        ));

        return new ImportContext(
                categoryByXmlId,
                productsBySku,
                attributeCache,
                valueCache
        );
    }

    private void flush(ImportContext ctx) {

        if (!ctx.getAttributesToSave().isEmpty()) {
            attributeRepository.saveAll(ctx.getAttributesToSave());
        }

        if (!ctx.getProductsToSave().isEmpty()) {
            productRepository.saveAll(ctx.getProductsToSave());
        }

        if (!ctx.getValuesToSave().isEmpty()) {
            valueRepository.saveAll(ctx.getValuesToSave());
        }
    }

    // ---- DTO результата ----
    public static class ImportResult {
        private final boolean success;
        private final int categoriesImported;
        private final int offersImported;
        private final String error;

        public ImportResult(boolean success,
                            int categoriesImported,
                            int offersImported,
                            String error) {
            this.success = success;
            this.categoriesImported = categoriesImported;
            this.offersImported = offersImported;
            this.error = error;
        }

        public boolean isSuccess() { return success; }
        public int getCategoriesImported() { return categoriesImported; }
        public int getOffersImported() { return offersImported; }
        public String getError() { return error; }
    }
}

