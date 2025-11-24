package ru.fisher.ToolsMarket.models;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "attribute")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Attribute {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name; // "Мощность", "Напряжение", "Вес"

    @Column(length = 100)
    private String unit; // "Вт", "В", "кг" - вынес отдельно от name

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AttributeType type;

    @Column(name = "sort_order")
    private Integer sortOrder = 0; // Порядок отображения

    @Column(length = 1024)
    private String options; // "18В;24В;36В" для SELECT

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false)
    private boolean required = false; // Обязательный атрибут

    @Column(name = "filterable")
    private boolean filterable = false; // Можно использовать в фильтрах
}
