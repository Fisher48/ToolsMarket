package ru.fisher.ToolsMarket.service;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import ru.fisher.ToolsMarket.models.Product;
import ru.fisher.ToolsMarket.models.ProductType;
import ru.fisher.ToolsMarket.models.User;
import ru.fisher.ToolsMarket.models.UserDiscount;
import ru.fisher.ToolsMarket.repository.UserDiscountRepository;
import ru.fisher.ToolsMarket.util.DiscountMath;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
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
            return normalize(discountOpt.get().getDiscountPercentage());
        }

        return BigDecimal.ZERO;
    }

    /**
     * Все активные скидки пользователя одним запросом — тип товара -> процент.
     * Нужен для страниц со списком товаров, где {@link #calculateDiscount} вызывается
     * на каждый товар и без этого даёт запрос на товар.
     */
    public Map<ProductType, BigDecimal> getDiscountsForUser(User user) {
        if (user == null || user.getUserType() == null) {
            return Map.of();
        }

        Map<ProductType, BigDecimal> discounts = new HashMap<>();
        for (UserDiscount discount : userDiscountRepository.findByUserType(user.getUserType())) {
            if (discount.isActive() && discount.getDiscountPercentage() != null) {
                discounts.put(discount.getProductType(), discount.getDiscountPercentage());
            }
        }
        return discounts;
    }

    /**
     * Процент скидки по уже загруженной карте скидок
     */
    public BigDecimal getDiscountPercentage(Map<ProductType, BigDecimal> discounts, Product product) {
        if (discounts == null || product == null || product.getProductType() == null) {
            return BigDecimal.ZERO;
        }
        return normalize(discounts.get(product.getProductType()));
    }

    /**
     * Расчет скидки в денежном выражении
     */
    public BigDecimal calculateDiscountAmount(User user, Product product, BigDecimal quantity) {
        BigDecimal discountPercentage = calculateDiscount(user, product);
        if (discountPercentage.compareTo(BigDecimal.ZERO) > 0) {
            return DiscountMath.lineDiscount(product.getPrice(), discountPercentage, quantity.longValue());
        }
        return BigDecimal.ZERO;
    }

    /**
     * Цена со скидкой
     */
    public BigDecimal getPriceWithDiscount(User user, Product product) {
        return getPriceWithDiscount(product, calculateDiscount(user, product));
    }

    /**
     * Цена со скидкой по уже посчитанному проценту (без повторного запроса).
     * Канон ({@link DiscountMath#unitPriceWithDiscount}): скидка на единицу
     * округляется один раз, далее только вычитание — так цена совпадает с
     * `OrderItem.unitPrice` в заказе.
     */
    public BigDecimal getPriceWithDiscount(Product product, BigDecimal discountPercentage) {
        if (product == null || product.getPrice() == null) {
            return BigDecimal.ZERO;
        }
        return DiscountMath.unitPriceWithDiscount(product.getPrice(), discountPercentage);
    }

    /**
     * Скидка в процентах не может быть null или отрицательной
     */
    private BigDecimal normalize(BigDecimal percentage) {
        return percentage == null ? BigDecimal.ZERO : percentage;
    }
}
