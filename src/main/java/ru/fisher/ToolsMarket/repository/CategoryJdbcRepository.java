package ru.fisher.ToolsMarket.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.fisher.ToolsMarket.dto.ProductDTO.ProductCardDto;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class CategoryJdbcRepository {

    private final JdbcTemplate jdbcTemplate;


    public Page<ProductCardDto> findProductsByCategory(
            Long categoryId,
            Long userId,
            String sort,
            int page,
            int size) {

        // 1. Получаем товары с пагинацией
        List<ProductCardDto> products = findProducts(categoryId, userId, sort, page, size);

        // 2. Получаем общее количество
        long total = countProductsByCategory(categoryId);

        return new PageImpl<>(products, PageRequest.of(page, size), total);
    }

    /**
     * Только товары в категории — без категории!
     */
    public List<ProductCardDto> findProducts(
            Long categoryId,
            Long userId,
            String sort,
            int page,
            int size) {

        String orderBy = switch (sort) {
            case "price_asc" -> "p.price ASC";
            case "price_desc" -> "p.price DESC";
            case "popularity" -> "p.views DESC";
            default -> "p.name ASC";
        };

        String productsSql = String.format("""
    SELECT 
        p.id,
        p.title,
        p.short_description,
        p.active,
        p.name,
        p.sku,
        p.price,
        (SELECT pi.url FROM product_image pi 
         WHERE pi.product_id = p.id 
         ORDER BY pi.sort_order LIMIT 1) as main_image_url,
        ud.discount_percentage,
        ROUND(p.price * (1 - COALESCE(ud.discount_percentage, 0) / 100), 2) as discounted_price,
        CASE WHEN ci.id IS NOT NULL THEN true ELSE false END as in_cart,
        COALESCE(ci.quantity, 0) as cart_quantity
    FROM product p
    JOIN product_category pc ON pc.product_id = p.id
    LEFT JOIN user_discounts ud ON ud.user_type = ?
        AND ud.product_type = p.product_type
        AND ud.is_active = true
    LEFT JOIN cart_item ci ON ci.product_id = p.id
        AND ci.cart_id = (SELECT id FROM cart WHERE user_id = ?)
    WHERE pc.category_id = ? AND p.active = true
    ORDER BY %s
    LIMIT ? OFFSET ?
    """, orderBy);

        String userType = userId != null ? getUserType(userId) : "REGULAR";

        return jdbcTemplate.query(
                productsSql,
                new Object[]{userType, userId, categoryId, size, page * size},
                (rs, rowNum) -> ProductCardDto.builder()
                        .id(rs.getLong("id"))
                        .title(rs.getString("title"))
                        .name(rs.getString("name"))
                        .sku(rs.getString("sku"))
                        .active(rs.getBoolean("active"))
                        .shortDescription(rs.getString("short_description"))
                        .price(rs.getBigDecimal("price"))
                        .mainImageUrl(rs.getString("main_image_url"))  // ← добавить
                        .discountPercentage(rs.getBigDecimal("discount_percentage"))
                        .discountedPrice(rs.getBigDecimal("discounted_price"))
                        .inCart(rs.getBoolean("in_cart"))
                        .cartQuantity(rs.getInt("cart_quantity"))
                        .build()
        );
    }

    public long countProductsByCategory(Long categoryId) {
        String sql = """
            SELECT COUNT(*)
            FROM product p
            JOIN product_category pc ON pc.product_id = p.id
            WHERE pc.category_id = ? AND p.active = true
            """;
        return jdbcTemplate.queryForObject(sql, new Object[]{categoryId}, Long.class);
    }

    private String getUserType(Long userId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT user_type FROM users WHERE id = ?",
                    new Object[]{userId},
                    String.class
            );
        } catch (Exception e) {
            return "REGULAR";
        }
    }
}
