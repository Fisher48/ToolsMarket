package ru.fisher.ToolsMarket.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class ParsedProductData {
    private String name;
    private String sku;
    private BigDecimal price;
    private String shortDescription;
    private String description;
    private List<String> imageUrls = new ArrayList<>();
}