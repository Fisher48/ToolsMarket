package ru.fisher.ToolsMarket.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.fisher.ToolsMarket.models.Product;
import ru.fisher.ToolsMarket.models.ProductType;
import ru.fisher.ToolsMarket.models.User;
import ru.fisher.ToolsMarket.models.UserDiscount;
import ru.fisher.ToolsMarket.models.UserType;
import ru.fisher.ToolsMarket.repository.UserDiscountRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Регрессии по скидкам: проценты вида 33.33 роняли страницы ArithmeticException,
 * а discount_percentage — nullable, что давало NPE.
 */
@ExtendWith(MockitoExtension.class)
class DiscountServiceTest {

    @Mock
    private UserDiscountRepository userDiscountRepository;

    @InjectMocks
    private DiscountService discountService;

    private Product product(ProductType type, String price) {
        return Product.builder()
                .name("Товар")
                .title("title")
                .price(new BigDecimal(price))
                .productType(type)
                .active(true)
                .build();
    }

    private User user(UserType type) {
        return User.builder()
                .username("u")
                .userType(type)
                .build();
    }

    private UserDiscount discount(UserType userType, ProductType productType, String percentage, boolean active) {
        return UserDiscount.builder()
                .userType(userType)
                .productType(productType)
                .discountPercentage(percentage == null ? null : new BigDecimal(percentage))
                .active(active)
                .build();
    }

    @Test
    void fractionalDiscountDoesNotThrow() {
        Product product = product(ProductType.TOOL, "1000.00");
        when(userDiscountRepository.findByUserTypeAndProductType(UserType.REGULAR, ProductType.TOOL))
                .thenReturn(Optional.of(discount(UserType.REGULAR, ProductType.TOOL, "33.33", true)));

        assertThatCode(() -> discountService.getPriceWithDiscount(user(UserType.REGULAR), product))
                .doesNotThrowAnyException();
    }

    @Test
    void priceWithFractionalDiscountIsRoundedToTwoDecimals() {
        Product product = product(ProductType.TOOL, "1000.00");
        when(userDiscountRepository.findByUserTypeAndProductType(UserType.REGULAR, ProductType.TOOL))
                .thenReturn(Optional.of(discount(UserType.REGULAR, ProductType.TOOL, "33.33", true)));

        // 1000 * (100 - 33.33) / 100 = 666.70
        assertThat(discountService.getPriceWithDiscount(user(UserType.REGULAR), product))
                .isEqualByComparingTo("666.70");
    }

    @Test
    void nullPercentageIsTreatedAsNoDiscount() {
        Product product = product(ProductType.TOOL, "1500.00");
        when(userDiscountRepository.findByUserTypeAndProductType(UserType.REGULAR, ProductType.TOOL))
                .thenReturn(Optional.of(discount(UserType.REGULAR, ProductType.TOOL, null, true)));

        assertThat(discountService.calculateDiscount(user(UserType.REGULAR), product))
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(discountService.getPriceWithDiscount(user(UserType.REGULAR), product))
                .isEqualByComparingTo("1500.00");
    }

    @Test
    void inactiveDiscountIsIgnored() {
        Product product = product(ProductType.TOOL, "1500.00");
        when(userDiscountRepository.findByUserTypeAndProductType(UserType.REGULAR, ProductType.TOOL))
                .thenReturn(Optional.of(discount(UserType.REGULAR, ProductType.TOOL, "10", false)));

        assertThat(discountService.calculateDiscount(user(UserType.REGULAR), product))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void fullDiscountGivesZeroPrice() {
        Product product = product(ProductType.TOOL, "1500.00");
        when(userDiscountRepository.findByUserTypeAndProductType(UserType.REGULAR, ProductType.TOOL))
                .thenReturn(Optional.of(discount(UserType.REGULAR, ProductType.TOOL, "100", true)));

        assertThat(discountService.getPriceWithDiscount(user(UserType.REGULAR), product))
                .isEqualByComparingTo("0.00");
    }

    @Test
    void discountsForUserLoadedWithSingleQuery() {
        when(userDiscountRepository.findByUserType(UserType.VIP)).thenReturn(List.of(
                discount(UserType.VIP, ProductType.TOOL, "33.33", true),
                discount(UserType.VIP, ProductType.HAND_TOOL, "5", true),
                discount(UserType.VIP, ProductType.OTHER, "7", false)));

        Map<ProductType, BigDecimal> discounts = discountService.getDiscountsForUser(user(UserType.VIP));

        assertThat(discounts).containsOnlyKeys(ProductType.TOOL, ProductType.HAND_TOOL);
        assertThat(discountService.getDiscountPercentage(discounts, product(ProductType.TOOL, "100.00")))
                .isEqualByComparingTo("33.33");
        assertThat(discountService.getDiscountPercentage(discounts, product(ProductType.OTHER, "100.00")))
                .isEqualByComparingTo(BigDecimal.ZERO);
        verify(userDiscountRepository).findByUserType(UserType.VIP);
    }

    @Test
    void anonymousUserDoesNotTouchRepository() {
        assertThat(discountService.getDiscountsForUser(null)).isEmpty();
        assertThat(discountService.calculateDiscount(null, product(ProductType.TOOL, "100.00")))
                .isEqualByComparingTo(BigDecimal.ZERO);

        verify(userDiscountRepository, never()).findByUserType(any());
    }
}
