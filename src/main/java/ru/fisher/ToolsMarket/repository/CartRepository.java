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

    // Оптимизированный запрос с загрузкой продуктов
    @Query("""
        SELECT DISTINCT c FROM Cart c
        LEFT JOIN FETCH c.items ci
        LEFT JOIN FETCH ci.product p
        LEFT JOIN FETCH p.images
        WHERE c.id = :id
    """)
    Optional<Cart> findByIdWithProducts(@Param("id") Long id);

    // Для пользователя
    @Query("""
        SELECT DISTINCT c FROM Cart c
        LEFT JOIN FETCH c.items ci
        LEFT JOIN FETCH ci.product p
        LEFT JOIN FETCH p.images
        WHERE c.user.id = :userId
    """)
    Optional<Cart> findByUserIdWithProducts(@Param("userId") Long userId);

    // Для сессии
    @Query("""
        SELECT DISTINCT c FROM Cart c
        LEFT JOIN FETCH c.items ci
        LEFT JOIN FETCH ci.product p
        LEFT JOIN FETCH p.images
        WHERE c.sessionId = :sessionId
    """)
    Optional<Cart> findBySessionIdWithProducts(@Param("sessionId") String sessionId);

//    // Старый метод для обратной совместимости
//    @Query("""
//        SELECT c FROM Cart c
//        LEFT JOIN FETCH c.items
//        WHERE c.user.id = :userId
//    """)
//    Optional<Cart> findWithItemsByUserId(@Param("userId") Long userId);
}
