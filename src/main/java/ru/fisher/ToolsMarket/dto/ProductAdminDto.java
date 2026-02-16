package ru.fisher.ToolsMarket.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Getter
@AllArgsConstructor
public class ProductAdminDto {
    private Long id;
    private String name;
    private String title;
    private String sku;
    private BigDecimal price;
    private boolean active;
    private String productType;
    private List<String> categories;
    private Instant createdAt;

    public String getProductType() {
        return productType != null ? productType : "—";
    }
}
