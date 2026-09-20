package ru.fisher.ToolsMarket.parsingXml;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * Сводка изменений по одному товару в рамках текущего запуска импорта.
 * Живёт только в памяти в пределах одного запроса импорта (не персистится) —
 * используется, чтобы показать админу "было → стало" сразу после
 * завершения импорта.
 */
@Getter
@Builder
public class ProductChangeSummary {

    public enum ChangeType { NEW, UPDATED }

    private String sku;
    private ChangeType changeType;

    private String oldName;
    private String newName;
    private boolean nameChanged;

    private BigDecimal oldPrice;
    private BigDecimal newPrice;
    private boolean priceChanged;

    private int oldImageCount;
    private int newImageCount;
    private boolean imagesChanged;

    private boolean descriptionChanged;
    private boolean attributesChanged;
}