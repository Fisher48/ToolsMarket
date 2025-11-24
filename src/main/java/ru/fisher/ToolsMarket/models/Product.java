package ru.fisher.ToolsMarket.models;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "product")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 512)
    private String name; // Название товара

    @Column(nullable = false, length = 512, unique = true)
    private String title; // Используется для человеко-читаемого URL

    @Column(length = 1024)
    private String shortDescription;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(unique = true, length = 100)
    private String sku; // Артикул

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price = BigDecimal.ZERO; // Цена

    @Column(nullable = false, length = 3)
    private String currency = "RUB"; // Валюта

    @Column(nullable = false)
    private boolean active = true; // Доступность

    @ManyToMany
    @JoinTable(
            name = "product_category",
            joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    private Set<Category> categories = new HashSet<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<ProductImage> images = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<ProductAttributeValue> attributeValues = new ArrayList<>();

    // Вспомогательный метод для получения значения атрибута
    public String getAttributeValue(String attributeName) {
        return attributeValues.stream()
                .filter(av -> av.getAttribute().getName().equals(attributeName))
                .map(ProductAttributeValue::getValue)
                .findFirst()
                .orElse(null);
    }

}
