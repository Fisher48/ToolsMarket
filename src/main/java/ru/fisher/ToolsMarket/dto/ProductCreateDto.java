package ru.fisher.ToolsMarket.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;
import ru.fisher.ToolsMarket.models.Product;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@Data
public class ProductCreateDto {
    private String name;
    private String title;
    private String shortDescription;
    private String description;
    private String sku;
    private BigDecimal price;
    private String currency;
    private boolean active = true;
    private List<Long> categoryIds;
    private List<MultipartFile> images;
    private List<String> imageAlts;
    private List<Integer> imageSortOrders;

    // Метод для преобразования в сущность Product
    public Product toEntity() {
        return Product.builder()
                .name(this.name)
                .title(this.title)
                .shortDescription(this.shortDescription)
                .description(this.description)
                .sku(this.sku)
                .price(this.price)
                .currency(this.currency)
                .active(this.active)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .categories(new HashSet<>())
                .images(new ArrayList<>())
                .build();
    }
}
