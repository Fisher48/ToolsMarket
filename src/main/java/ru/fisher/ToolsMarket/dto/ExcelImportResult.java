package ru.fisher.ToolsMarket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExcelImportResult {

    @Builder.Default
    private int totalRows = 0;

    @Builder.Default
    private int created = 0;

    @Builder.Default
    private int skipped = 0;

    @Builder.Default
    private int errors = 0;

    @Builder.Default
    private List<String> errorMessages = new ArrayList<>();

    @Builder.Default
    private List<String> createdProducts = new ArrayList<>();

    @Builder.Default
    private List<String> skippedProducts = new ArrayList<>();

    public void incrementCreated() {
        created++;
    }

    public void incrementSkipped() {
        skipped++;
    }

    public void incrementErrors() {
        errors++;
    }

    public void addError(String error) {
        errorMessages.add(error);
        errors++;
    }

    public void addCreatedProduct(String sku) {
        createdProducts.add(sku);
    }

    public void addSkippedProduct(String sku, String reason) {
        skippedProducts.add(sku + " (" + reason + ")");
    }
}