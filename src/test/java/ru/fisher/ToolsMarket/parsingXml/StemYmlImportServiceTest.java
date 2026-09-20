package ru.fisher.ToolsMarket.parsingXml;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.fisher.ToolsMarket.models.Attribute;
import ru.fisher.ToolsMarket.models.AttributeType;
import ru.fisher.ToolsMarket.models.Category;
import ru.fisher.ToolsMarket.models.Product;
import ru.fisher.ToolsMarket.models.ProductType;
import ru.fisher.ToolsMarket.repository.*;

import java.io.File;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Тесты оркестрации StemYmlImportService. Реальные YmlCategoryImporter /
 * YmlOfferImporter здесь НЕ мокаются намеренно — используется настоящий
 * XML небольшого размера, чтобы проверить сквозной сценарий: скачивание,
 * предзагрузку SKU, вызов саб-импортеров, батчинг сохранения и статистику.
 * Репозитории — моки.
 */
@ExtendWith(MockitoExtension.class)
class StemYmlImportServiceTest {

    @Mock private ProductRepository productRepository;
    @Mock private ProductAttributeValueRepository valueRepository;
    @Mock private ProductImageRepository imageRepository;
    @Mock private AttributeRepository attributeRepository;

    private static final String SAMPLE_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <yml_catalog date="2026-08-29 10:00">
                <shop>
                    <categories>
                        <category id="1">Инструменты</category>
                        <category id="2" parentId="1">Дрели</category>
                    </categories>
                    <offers>
                        <offer id="1001">
                            <name>Дрель новая</name>
                            <price>3000</price>
                            <vendorCode>NEW-1</vendorCode>
                            <categoryId>2</categoryId>
                        </offer>
                        <offer id="1002">
                            <name>Дрель существующая</name>
                            <price>4500</price>
                            <vendorCode>EXIST-1</vendorCode>
                            <categoryId>2</categoryId>
                        </offer>
                    </offers>
                </shop>
            </yml_catalog>
            """;

    private String writeTempXmlFile() throws Exception {
        File tmp = File.createTempFile("yml-feed", ".xml");
        tmp.deleteOnExit();
        Files.writeString(tmp.toPath(), SAMPLE_XML, StandardCharsets.UTF_8);
        return tmp.toURI().toURL().toString();
    }

    @Test
    void importFromUrl_preloadsExistingProductsBySku_toAvoidNplusOne() throws Exception {
        when(attributeRepository.findAll()).thenReturn(List.of());

        Product existing = new Product();
        existing.setId(42L);
        existing.setSku("EXIST-1");
        existing.setName("Дрель существующая");
        existing.setPrice(new BigDecimal("4000"));
        existing.setActive(true);

        // Предзагрузка всех SKU одним запросом (с деталями — картинки/атрибуты)
        when(productRepository.findAllBySkusWithDetails(anySet())).thenReturn(List.of(existing));

        StemYmlImportService service = newService();

        String url = writeTempXmlFile();
        StemYmlImportService.ImportResult result = service.importFromUrl(url, 1L);

        // Предзагрузка вызвана РОВНО один раз с обоими SKU, а не по одному на оффер
        ArgumentCaptor<Set<String>> skuCaptor = ArgumentCaptor.forClass(Set.class);
        verify(productRepository, times(1)).findAllBySkusWithDetails(skuCaptor.capture());
        assertThat(skuCaptor.getValue()).containsExactlyInAnyOrder("NEW-1", "EXIST-1");

        // findBySku (fallback точечный запрос) не должен вызываться для товара,
        // который уже был предзагружен (EXIST-1). Для genuinely нового NEW-1
        // fallback допустим, т.к. его нет в предзагруженном кэше.
        verify(productRepository, never()).findBySku(eq("EXIST-1"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getCategoriesImported()).isEqualTo(2);
        assertThat(result.getOffersImported()).isEqualTo(2);
        assertThat(result.getNewProducts()).isEqualTo(1);      // NEW-1
        assertThat(result.getUpdatedProducts()).isEqualTo(1);  // EXIST-1 (цена изменилась)
        assertThat(result.isDryRun()).isFalse();

        // Кто делал изменения: у нового товара — обе роли, у изменённого — updatedBy
        ArgumentCaptor<List<Product>> saveCaptor = ArgumentCaptor.forClass(List.class);
        verify(productRepository, atLeastOnce()).saveAll(saveCaptor.capture());
        List<Product> saved = saveCaptor.getAllValues().stream()
                .flatMap(List::stream)
                .toList();
        Product newProduct = saved.stream().filter(p -> "NEW-1".equals(p.getSku())).findFirst().orElseThrow();
        assertThat(newProduct.getCreatedByUserId()).isEqualTo(1L);
        assertThat(newProduct.getUpdatedByUserId()).isEqualTo(1L);

        Product updatedProduct = saved.stream().filter(p -> "EXIST-1".equals(p.getSku())).findFirst().orElseThrow();
        assertThat(updatedProduct.getUpdatedByUserId()).isEqualTo(1L);
    }

    @Test
    void importFromUrl_savesProductsAndValuesInBatches() throws Exception {
        when(attributeRepository.findAll()).thenReturn(List.of());
        when(productRepository.findAllBySkusWithDetails(anySet())).thenReturn(List.of());

        StemYmlImportService service = newService();

        String url = writeTempXmlFile();
        service.importFromUrl(url, 1L);

        // Оба товара новые -> должны быть сохранены через saveAll (батч)
        verify(productRepository, atLeastOnce()).saveAll(anyList());
    }

    @Test
    void previewFromUrl_doesNotPersistAnything_butReportsSameChanges() throws Exception {
        when(attributeRepository.findAll()).thenReturn(List.of());

        Product existing = new Product();
        existing.setId(42L);
        existing.setSku("EXIST-1");
        existing.setName("Дрель существующая");
        existing.setPrice(new BigDecimal("4000"));
        existing.setActive(true);

        when(productRepository.findAllBySkusWithDetails(anySet()))
                .thenReturn(List.of(existing));

        StemYmlImportService service = newService();

        String url = writeTempXmlFile();
        StemYmlImportService.ImportResult result = service.previewFromUrl(url, 1L);

        // Никаких записей в БД в режиме предпросмотра
        verify(productRepository, never()).saveAll(anyList());
        verify(productRepository, never()).save(any(Product.class));
        verify(valueRepository, never()).saveAll(anyList());
        verify(imageRepository, never()).saveAll(anyList());

        // Но статистика и список изменений совпадают с реальным импортом
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isDryRun()).isTrue();
        assertThat(result.getCategoriesImported()).isEqualTo(2);
        assertThat(result.getOffersImported()).isEqualTo(2);
        assertThat(result.getNewProducts()).isEqualTo(1);
        assertThat(result.getUpdatedProducts()).isEqualTo(1);
        assertThat(result.getChanges()).hasSize(2);
    }

    @Test
    void importFromUrl_doesNotResetActive_productType_orCategory_ofExistingProduct() throws Exception {
        when(attributeRepository.findAll()).thenReturn(List.of());

        Category feedCategory = Category.builder()
                .id(2L)
                .name("Дрели")
                .title("dreli")
                .build();

        // Существующий товар деактивирован, имеет свой тип и свою категорию
        // — импорт не должен их трогать (баг №4 из FirstSession.md).
        Product existing = new Product();
        existing.setId(7L);
        existing.setSku("EXIST-1");
        existing.setName("Дрель существующая");
        existing.setPrice(new BigDecimal("4500"));
        existing.setActive(false);
        existing.setProductType(ProductType.ZUBR);
        existing.getCategories().add(feedCategory);

        when(productRepository.findAllBySkusWithDetails(anySet()))
                .thenReturn(List.of(existing));

        StemYmlImportService service = newService();

        String url = writeTempXmlFile();
        StemYmlImportService.ImportResult result = service.importFromUrl(url, 1L);

        // Цена совпала -> изменений для существующего товара нет,
        // в список на сохранение попадает только новый NEW-1.
        assertThat(result.getUpdatedProducts()).isZero();

        // Существующий товар сохранил своё состояние
        assertThat(existing.isActive()).isFalse();
        assertThat(existing.getProductType()).isEqualTo(ProductType.ZUBR);
        assertThat(existing.getCategories()).containsExactly(feedCategory);

        // Сохраняется только новый товар, но не существующий
        ArgumentCaptor<List<Product>> saveCaptor = ArgumentCaptor.forClass(List.class);
        verify(productRepository).saveAll(saveCaptor.capture());
        List<String> savedSkus = saveCaptor.getAllValues().stream()
                .flatMap(List::stream)
                .map(Product::getSku)
                .toList();
        assertThat(savedSkus).containsExactly("NEW-1");
    }

    @Test
    void importFromUrl_priceScaleDifferenceIsNotAnUpdate() throws Exception {
        when(attributeRepository.findAll()).thenReturn(List.of());

        // В БД цена хранится с двумя знаками, в фиде — без. Численно они равны,
        // поэтому пересохранения и ложного лога "ЦЕНА: ... -> ..." быть не должно.
        Product existing = new Product();
        existing.setId(5L);
        existing.setSku("EXIST-1");
        existing.setName("Дрель существующая");
        existing.setPrice(new BigDecimal("47500.00"));
        existing.setActive(true);
        existing.setUpdatedByUserId(999L); // автор последних правок, задан вручную

        when(productRepository.findAllBySkusWithDetails(anySet()))
                .thenReturn(List.of(existing));

        StemYmlImportService service = newService();

        String url = writeTempXmlFilePrice("47500");
        StemYmlImportService.ImportResult result = service.importFromUrl(url, 1L);

        assertThat(result.getNewProducts()).isZero();
        assertThat(result.getUpdatedProducts()).isZero();

        // Цена численно не изменилась -> товар не пересохраняем
        verify(productRepository, never()).saveAll(anyList());

        // И значение цены в БД не мутируется (масштаб сохраняется)
        assertThat(existing.getPrice()).isEqualByComparingTo("47500.00");

        // Автора правок не затираем: idempotent-импорт не проставляет updatedBy
        assertThat(existing.getUpdatedByUserId()).isEqualTo(999L);
    }

    @Test
    void importFromUrl_disambiguatesCollidingVendorCodes_intoDistinctSkus() throws Exception {
        when(attributeRepository.findAll()).thenReturn(List.of());
        when(productRepository.findAllBySkusWithDetails(anySet())).thenReturn(List.of());

        // Два РАЗНЫХ товара в фиде с одним vendorCode (как на stem: КАТ020 = SVR-501H
        // и SVR-501HS). Импорт не должен слить их в один товар и не должен падать.
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <yml_catalog date="2026-08-29 10:00">
                    <shop>
                        <categories>
                            <category id="2">Виброкатки</category>
                        </categories>
                        <offers>
                            <offer id="709">
                                <name>Виброкаток SVR-501H</name>
                                <price>470000</price>
                                <vendorCode>КАТ020</vendorCode>
                                <categoryId>2</categoryId>
                            </offer>
                            <offer id="3620">
                                <name>Виброкаток SVR-501HS</name>
                                <price>505000</price>
                                <vendorCode>КАТ020</vendorCode>
                                <categoryId>2</categoryId>
                            </offer>
                        </offers>
                    </shop>
                </yml_catalog>
                """;
        String url = writeTempXml(xml);

        StemYmlImportService service = newService();
        StemYmlImportService.ImportResult result = service.importFromUrl(url, 1L);

        // Предзагрузка идёт по дизамбигированным SKU
        ArgumentCaptor<Set<String>> skuCaptor = ArgumentCaptor.forClass(Set.class);
        verify(productRepository, times(1)).findAllBySkusWithDetails(skuCaptor.capture());
        assertThat(skuCaptor.getValue())
                .containsExactlyInAnyOrder("КАТ020-709", "КАТ020-3620");

        // Сохранены оба товара, каждый под своим уникальным SKU
        ArgumentCaptor<List<Product>> saveCaptor = ArgumentCaptor.forClass(List.class);
        verify(productRepository, atLeastOnce()).saveAll(saveCaptor.capture());
        List<String> savedSkus = saveCaptor.getAllValues().stream()
                .flatMap(List::stream)
                .map(Product::getSku)
                .toList();
        assertThat(savedSkus).containsExactlyInAnyOrder("КАТ020-709", "КАТ020-3620");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getNewProducts()).isEqualTo(2);
    }

    @Test
    void importFromUrl_toleratesDuplicateAttributes_insteadOfCrashing() throws Exception {
        // В БД исторически два атрибута с одинаковыми (category_id, name),
        // как '44_Мощность' на проде. Импорт не должен падать с
        // IllegalStateException "Duplicate key" при построении кэша атрибутов.
        Category cat = Category.builder().id(2L).name("Дрели").title("dreli").build();
        Attribute attr1 = Attribute.builder()
                .id(10L).name("Мощность").type(AttributeType.STRING).category(cat).build();
        Attribute attr2 = Attribute.builder()
                .id(11L).name("Мощность").type(AttributeType.STRING).category(cat).build();

        when(attributeRepository.findAll()).thenReturn(List.of(attr1, attr2));
        when(productRepository.findAllBySkusWithDetails(anySet())).thenReturn(List.of());
        // Категория из фида — новый инстанс со случайным id, кэш атрибутов по ней
        // промахнётся, и импорт создаст атрибут через save (возвращаем аргумент).
        when(attributeRepository.save(any(Attribute.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <yml_catalog date="2026-08-29 10:00">
                    <shop>
                        <categories>
                            <category id="2">Дрели</category>
                        </categories>
                        <offers>
                            <offer id="1001">
                                <name>Дрель новая</name>
                                <price>3000</price>
                                <vendorCode>NEW-1</vendorCode>
                                <categoryId>2</categoryId>
                                <param name="Мощность">2200</param>
                            </offer>
                        </offers>
                    </shop>
                </yml_catalog>
                """;
        String url = writeTempXml(xml);

        StemYmlImportService service = newService();
        StemYmlImportService.ImportResult result = service.importFromUrl(url, 1L);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getNewProducts()).isEqualTo(1);
    }

    @Test
    void previewFromUrl_toleratesDuplicateAttributes_insteadOfCrashing() throws Exception {
        // Предпросмотр (dry-run) — тоже строит кэш атрибутов из findAll(),
        // поэтому дубли в БД не должны ронять и его.
        Category cat = Category.builder().id(2L).name("Дрели").title("dreli").build();
        Attribute attr1 = Attribute.builder()
                .id(10L).name("Мощность").type(AttributeType.STRING).category(cat).build();
        Attribute attr2 = Attribute.builder()
                .id(11L).name("Мощность").type(AttributeType.STRING).category(cat).build();

        when(attributeRepository.findAll()).thenReturn(List.of(attr1, attr2));
        when(productRepository.findAllBySkusWithDetails(anySet())).thenReturn(List.of());

        StemYmlImportService service = newService();
        String url = writeTempXmlFile();

        StemYmlImportService.ImportResult result = service.previewFromUrl(url, 1L);

        assertThat(result.isSuccess()).isTrue();
        verify(attributeRepository, never()).save(any(Attribute.class));
    }

    @Test
    void importFromUrl_nonCollidingVendorCode_keepsSkuUntouched() throws Exception {
        when(attributeRepository.findAll()).thenReturn(List.of());
        when(productRepository.findAllBySkusWithDetails(anySet())).thenReturn(List.of());

        StemYmlImportService service = newService();
        String url = writeTempXmlFile(); // vendorCode NEW-1 и EXIST-1 — без коллизий

        service.importFromUrl(url, 1L);

        ArgumentCaptor<Set<String>> skuCaptor = ArgumentCaptor.forClass(Set.class);
        verify(productRepository, times(1)).findAllBySkusWithDetails(skuCaptor.capture());
        assertThat(skuCaptor.getValue()).containsExactlyInAnyOrder("NEW-1", "EXIST-1");
    }

    private String writeTempXml(String xml) throws Exception {
        File tmp = File.createTempFile("yml-feed", ".xml");
        tmp.deleteOnExit();
        Files.writeString(tmp.toPath(), xml, StandardCharsets.UTF_8);
        return tmp.toURI().toURL().toString();
    }

    private String writeTempXmlFilePrice(String priceText) throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <yml_catalog date="2026-08-29 10:00">
                    <shop>
                        <categories>
                            <category id="2">Дрели</category>
                        </categories>
                        <offers>
                            <offer id="1002">
                                <name>Дрель существующая</name>
                                <price>%s</price>
                                <vendorCode>EXIST-1</vendorCode>
                                <categoryId>2</categoryId>
                            </offer>
                        </offers>
                    </shop>
                </yml_catalog>
                """.formatted(priceText);
        File tmp = File.createTempFile("yml-feed", ".xml");
        tmp.deleteOnExit();
        Files.writeString(tmp.toPath(), xml, StandardCharsets.UTF_8);
        return tmp.toURI().toURL().toString();
    }

    private StemYmlImportService newService() {
        return new StemYmlImportService(
                new YmlCategoryImporter(mockCategoryRepository()),
                new YmlOfferImporter(valueRepository, productRepository, attributeRepository),
                productRepository, valueRepository, imageRepository, attributeRepository
        );
    }

    private CategoryRepository mockCategoryRepository() {
        CategoryRepository repo = mock(CategoryRepository.class);

        when(repo.findByTitle(anyString())).thenReturn(Optional.empty());
        lenient().when(repo.save(any(Category.class))).thenAnswer(invocation -> {
            Category c = invocation.getArgument(0);
            if (c.getId() == null) {
                // эмулируем генерацию id при вставке
                c = Category.builder()
                        .id((long) (Math.random() * 1_000_000))
                        .name(c.getName())
                        .title(c.getTitle())
                        .parent(c.getParent())
                        .createdAt(c.getCreatedAt())
                        .sortOrder(c.getSortOrder())
                        .build();
            }
            return c;
        });
        return repo;
    }
}
