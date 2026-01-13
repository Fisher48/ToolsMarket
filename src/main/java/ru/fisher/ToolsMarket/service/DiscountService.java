package ru.fisher.ToolsMarket.service;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import ru.fisher.ToolsMarket.models.*;
import ru.fisher.ToolsMarket.repository.UserDiscountRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

@Service
@AllArgsConstructor
public class DiscountService {

    private final UserDiscountRepository userDiscountRepository;

    /**
     * Расчет скидки для пользователя на конкретный товар
     */
    public BigDecimal calculateDiscount(User user, Product product) {
        if (user == null || product == null || user.getUserType() == null) {
            return BigDecimal.ZERO;
        }

        // Получаем скидку для комбинации типа пользователя и типа товара
        Optional<UserDiscount> discountOpt = userDiscountRepository
                .findByUserTypeAndProductType(user.getUserType(), product.getProductType());

        if (discountOpt.isPresent() && discountOpt.get().isActive()) {
            return discountOpt.get().getDiscountPercentage();
        }

        return BigDecimal.ZERO;
    }

    /**
     * Расчет скидки в денежном выражении
     */
    public BigDecimal calculateDiscountAmount(User user, Product product, BigDecimal quantity) {
        BigDecimal discountPercentage = calculateDiscount(user, product);
        if (discountPercentage.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal price = product.getPrice();
            BigDecimal discountAmount = price
                    .multiply(discountPercentage)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                    .multiply(quantity);
            return discountAmount;
        }
        return BigDecimal.ZERO;
    }

    /**
     * Цена со скидкой
     */
    public BigDecimal getPriceWithDiscount(User user, Product product) {
        BigDecimal discountPercentage = calculateDiscount(user, product);
        if (discountPercentage.compareTo(BigDecimal.ZERO) > 0) {
            return product.getPrice()
                    .multiply(BigDecimal.ONE.subtract(discountPercentage.divide(BigDecimal.valueOf(100))))
                    .setScale(2, RoundingMode.HALF_UP);
        }
        return product.getPrice();
    }
}
