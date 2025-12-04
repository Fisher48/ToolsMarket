package ru.fisher.ToolsMarket.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.fisher.ToolsMarket.models.OrderItem;

import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    List<OrderItem> findByOrderId(Long orderId);
}
