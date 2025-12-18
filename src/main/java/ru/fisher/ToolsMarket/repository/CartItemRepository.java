package ru.fisher.ToolsMarket.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.fisher.ToolsMarket.models.Cart;
import ru.fisher.ToolsMarket.models.CartItem;

import java.util.List;
import java.util.Optional;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    List<CartItem> findAllByCart(Cart cart);
    List<CartItem> findByCartId(Long cartId);
    Optional<CartItem> findByCartIdAndProductId(Long cartId, Long productId);
    
    @Modifying
    @Query("DELETE FROM CartItem ci WHERE ci.cart.id = :cartId")
    void deleteByCartId(@Param("cartId") Long cartId);

    @Modifying
    @Query("DELETE FROM CartItem ci WHERE ci.cart.id = :cartId AND ci.productId = :productId")
    void deleteByCartIdAndProductId(@Param("cartId") Long cartId, @Param("productId") Long productId);
}
