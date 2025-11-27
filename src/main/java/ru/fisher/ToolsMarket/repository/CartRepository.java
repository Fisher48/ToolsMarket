package ru.fisher.ToolsMarket.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.fisher.ToolsMarket.models.Cart;

import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long> {
    Optional<Cart> findBySessionId(String sessionId);
    Optional<Cart> findByUserId(Long userId);
}
