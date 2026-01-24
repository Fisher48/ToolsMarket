package ru.fisher.ToolsMarket.service;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.api.Test;
import ru.fisher.ToolsMarket.dto.PriceRow;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

class PriceExcelParserTest {
    private final PriceExcelParser parser = new PriceExcelParser();

    @Test
    void shouldParseOnlyValidPriceRows() throws Exception {

        // given
        ByteArrayInputStream excel = createTestExcel();

        // when
        List<PriceRow> rows = parser.parse(excel);

        // then
        assertThat(rows)
                .hasSize(3)
                .extracting(
                        PriceRow::sku,
                        row -> row.price().stripTrailingZeros()
                )
                .containsExactlyInAnyOrder(
                        tuple("HCD18350S", new BigDecimal("12990").stripTrailingZeros()),
                        tuple("CD20350BL", new BigDecimal("3990").stripTrailingZeros()),
                        tuple("ГА-350", new BigDecimal("6990").stripTrailingZeros())
                );
    }

    private ByteArrayInputStream createTestExcel() throws IOException {
        Workbook workbook = new HSSFWorkbook();
        Sheet sheet = workbook.createSheet("Price");

        // мусор и категории
        sheet.createRow(0).createCell(0).setCellValue("Аккумуляторные инструменты");
        sheet.createRow(1).createCell(0).setCellValue("Гайковерты");

        // заголовок
        Row header = sheet.createRow(2);
        header.createCell(1).setCellValue("Артикул  для заказа");
        header.createCell(3).setCellValue("Прайс,  руб.");

        // валидные товары
        Row r1 = sheet.createRow(3);
        r1.createCell(1).setCellValue("HCD18350S");
        r1.createCell(3).setCellValue("12\u00A0990,0");

        Row r2 = sheet.createRow(4);
        r2.createCell(1).setCellValue("CD20350BL");
        r2.createCell(3).setCellValue("3 990");

        Row r3 = sheet.createRow(5);
        r3.createCell(1).setCellValue("ГА-350");
        r3.createCell(3).setCellValue(6990);

        // строка без цены → должна быть проигнорирована
        Row invalid = sheet.createRow(6);
        invalid.createCell(1).setCellValue("NO_PRICE");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        workbook.close();

        return new ByteArrayInputStream(out.toByteArray());
    }

}