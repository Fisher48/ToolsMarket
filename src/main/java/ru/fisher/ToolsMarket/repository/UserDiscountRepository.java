package ru.fisher.ToolsMarket.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.fisher.ToolsMarket.models.ProductType;
import ru.fisher.ToolsMarket.models.UserDiscount;
import ru.fisher.ToolsMarket.models.UserType;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserDiscountRepository extends JpaRepository<UserDiscount, Long> {

    Optional<UserDiscount> findByUserTypeAndProductType(UserType userType, ProductType productType);

    List<UserDiscount> findByUserType(UserType userType);

    List<UserDiscount> findByProductType(ProductType productType);

    List<UserDiscount> findByActiveTrue();
}
