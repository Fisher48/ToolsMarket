package ru.fisher.ToolsMarket.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.fisher.ToolsMarket.models.Order;
import ru.fisher.ToolsMarket.models.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    @Query("""
        SELECT o FROM Order o
        LEFT JOIN FETCH o.orderItems
        WHERE o.id = :id
    """)
    Optional<Order> findByIdWithItems(Long id);

    @EntityGraph(attributePaths = {
            "orderItems",
            "orderItems.product",  // Загружаем продукт через цепочку
            "orderItems.product.images",  // Если нужны изображения
            "user",  // Добавляем загрузку user
            "user.userType"
    })
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdWithItemsAndProduct(@Param("id") Long id);

    @Query("SELECT DISTINCT o FROM Order o " +
            "LEFT JOIN FETCH o.orderItems oi " +
            "LEFT JOIN FETCH oi.product " +
            "WHERE o.user.id = :userId " +
            "ORDER BY o.createdAt DESC")
    List<Order> findByUserIdWithItems(@Param("userId") Long userId);

    // 1. Для всех заказов
    @EntityGraph(attributePaths = {"user", "user.userType", "orderItems"})
    @Query("SELECT o FROM Order o ORDER BY o.createdAt DESC")
    List<Order> findAllByOrderByCreatedAtDesc();

    // 2. Для заказов по статусу
    @EntityGraph(attributePaths = {"user", "user.userType", "orderItems"})
    @Query("SELECT o FROM Order o WHERE o.status = :status ORDER BY o.createdAt DESC")
    List<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status);

    long countByStatus(OrderStatus status);

    Optional<Order> findByOrderNumber(Long orderNumber);

    // Поиск заказов по SKU товара
    @Query("SELECT DISTINCT o " +
            "FROM Order o " +
            "JOIN o.orderItems oi " +
            "WHERE oi.productSku LIKE :sku")
    List<Order> searchByProductSku(@Param("sku") String sku);

    // Подсчет общей выручки
    @Query("SELECT COALESCE(SUM(o.totalPrice), 0) " +
            "FROM Order o " +
            "WHERE o.status = 'COMPLETED'")
    BigDecimal calculateTotalRevenue();

    // Заказы после определенной даты
    List<Order> findByCreatedAtAfterOrderByCreatedAtDesc(Instant date);

    // Статистика по статусам
    @Query("SELECT o.status, COUNT(o) FROM Order o GROUP BY o.status")
    List<Object[]> countByStatusGroup();

    // Новый метод для заказов по статусу
    @EntityGraph(attributePaths = {
            "user",
            "user.userType",
            "orderItems",
            "orderItems.product"
    })
    @Query("SELECT DISTINCT o FROM Order o " +
            "WHERE o.user.id = :userId AND o.status = :status " +
            "ORDER BY o.createdAt DESC")
    List<Order> findByUserIdAndStatusWithProducts(@Param("userId") Long userId,
                                                  @Param("status") OrderStatus status);


    // Поиск заказов пользователя
    @EntityGraph(attributePaths = {"user", "user.userType", "orderItems", "orderItems.product"})
    List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<Order> findByIdAndUserId(Long id, Long userId);

    List<Order> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, OrderStatus status);

}
