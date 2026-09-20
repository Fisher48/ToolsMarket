package ru.fisher.ToolsMarket.service;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import ru.fisher.ToolsMarket.models.Category;
import ru.fisher.ToolsMarket.models.Product;
import ru.fisher.ToolsMarket.repository.CategoryRepository;
import ru.fisher.ToolsMarket.repository.ProductImageRepository;
import ru.fisher.ToolsMarket.repository.ProductRepository;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Тесты ExcelProductImportService: при создании товара импортом проставляется
 * "кто создал и кто изменил" (createdByUserId / updatedByUserId) — автор импорта.
 */
@ExtendWith(MockitoExtension.class)
class ExcelProductImportServiceTest {

    @Mock private ProductRepository productRepository;
    @Mock private ProductImageRepository productImageRepository;
    @Mock private CategoryRepository categoryRepository;

    private ExcelProductImportService service;

    @BeforeEach
    void setUp() {
        service = new ExcelProductImportService(productRepository, productImageRepository, categoryRepository);
        ReflectionTestUtils.setField(service, "batchSize", 100);
    }

    @Test
    void importFromExcel_setsCreatedAndUpdatedBy_ofNewProducts() throws Exception {
        Category category = Category.builder().id(1L).name("Импортированные товары").title("ruchnoy_instrument").build();
        when(categoryRepository.findByTitle("ruchnoy_instrument")).thenReturn(Optional.of(category));
        when(productRepository.findAllSkus()).thenReturn(Set.of());
        when(productRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        MockMultipartFile file = new MockMultipartFile("file", "products.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                buildXlsx());

        service.importFromExcel(file, 7L);

        ArgumentCaptor<List<Product>> saveCaptor = ArgumentCaptor.forClass(List.class);
        verify(productRepository).saveAll(saveCaptor.capture());

        List<Product> saved = saveCaptor.getAllValues().stream()
                .flatMap(List::stream)
                .toList();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getSku()).isEqualTo("XL-1");
        assertThat(saved.get(0).getCreatedByUserId()).isEqualTo(7L);
        assertThat(saved.get(0).getUpdatedByUserId()).isEqualTo(7L);
    }

    private byte[] buildXlsx() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Лист1");
            var header = sheet.createRow(0);
            String[] columns = {"Артикул", "Наименование элемента", "Детальное описание", "Детальная картинка"};
            for (int i = 0; i < columns.length; i++) {
                header.createCell(i).setCellValue(columns[i]);
            }
            sheet.createRow(1).createCell(0).setCellValue("XL-1");
            sheet.getRow(1).createCell(1).setCellValue("Шуруповёрт XL-1");
            sheet.getRow(1).createCell(2).setCellValue("Описание товара");
            workbook.write(out);
            return out.toByteArray();
        }
    }
}