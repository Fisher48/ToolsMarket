package ru.fisher.ToolsMarket.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.fisher.ToolsMarket.models.Category;
import ru.fisher.ToolsMarket.models.Product;

import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByTitle(String title);

    @Query("SELECT p FROM Product p " +
            "JOIN p.categories c " +
            "WHERE c.id = :categoryId AND p.active = true")
    Page<Product> findActiveByCategory(@Param("categoryId") Long categoryId, Pageable pageable);

    @Query("SELECT p FROM Product p " +
            "WHERE p.active = true " +
            "AND (LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "OR LOWER(p.shortDescription) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "OR LOWER(p.description) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :q, '%'))) " +
            "ORDER BY " +
            "CASE WHEN LOWER(p.name) LIKE LOWER(CONCAT(:q, '%')) THEN 1 " +
            "     WHEN LOWER(p.sku) = LOWER(:q) THEN 2 " +
            "     ELSE 3 END, p.name")
    Page<Product> searchActive(@Param("q") String q, Pageable pageable);

    boolean existsByTitle(String title);

    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.images LEFT JOIN FETCH p.categories WHERE p.id = :id")
    Optional<Product> findByIdWithImagesAndCategories(@Param("id") Long id);

    boolean existsByCategoriesContaining(Category category);

    Page<Product> findByActiveTrue(Pageable pageable);

//    @Query("SELECT p FROM Product p " +
//            "WHERE p.active = true " +
//            "AND (LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')) " +
//            "OR p.description LIKE CONCAT('%', :q, '%'))")
//    Page<Product> searchActive(@Param("q") String q, Pageable pageable);
}
