package ru.fisher.ToolsMarket.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.fisher.ToolsMarket.models.ProductAttributeValue;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductAttributeValueRepository extends JpaRepository<ProductAttributeValue, Long> {

    List<ProductAttributeValue> findByProductId(Long productId);

    List<ProductAttributeValue> findByProductIdOrderBySortOrder(Long productId);

    @Query("SELECT pav FROM ProductAttributeValue pav " +
            "WHERE pav.product.id = :productId AND pav.attribute.filterable = true")
    List<ProductAttributeValue> findFilterableByProductId(@Param("productId") Long productId);

    Optional<ProductAttributeValue> findByProductIdAndAttributeId(Long productId, Long attributeId);

    @Modifying
    @Query("DELETE FROM ProductAttributeValue pav WHERE pav.product.id = :productId")
    void deleteByProductId(@Param("productId") Long productId);

    @Modifying
    @Query("DELETE FROM ProductAttributeValue pav WHERE pav.attribute.id = :attributeId")
    void deleteByAttributeId(@Param("attributeId") Long attributeId);

    @Query("SELECT pav FROM ProductAttributeValue pav " +
            "WHERE pav.attribute.id = :attributeId AND pav.value = :value")
    List<ProductAttributeValue> findByAttributeIdAndValue(@Param("attributeId") Long attributeId,
                                                          @Param("value") String value);

    @Query("SELECT DISTINCT pav.value FROM ProductAttributeValue pav " +
            "WHERE pav.attribute.id = :attributeId ORDER BY pav.value")
    List<String> findDistinctValuesByAttributeId(@Param("attributeId") Long attributeId);
}
