package ru.fisher.ToolsMarket.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.fisher.ToolsMarket.models.Cart;

import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long> {
    Optional<Cart> findBySessionId(String sessionId);
    Optional<Cart> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    // Новый метод для поиска по пользователю с предзагрузкой items
    @EntityGraph(attributePaths = {"items"})
    Optional<Cart> findWithItemsByUserId(Long userId);

    @Modifying
    @Query("DELETE FROM Cart c WHERE c.user = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
