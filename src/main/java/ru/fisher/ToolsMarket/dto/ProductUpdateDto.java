package ru.fisher.ToolsMarket.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ProductUpdateDto {
    private String name;
    private String title;
    private String shortDescription;
    private String description;
    private String sku;
    private BigDecimal price;
    private String currency;
    private boolean active;
    private List<Long> categoryIds;
    private List<MultipartFile> newImages;
    private List<String> newImageAlts;
    private List<Integer> newImageSortOrders;
    private List<Long> deleteImageIds;
}
