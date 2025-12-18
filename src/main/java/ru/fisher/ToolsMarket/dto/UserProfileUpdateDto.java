package ru.fisher.ToolsMarket.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileUpdateDto {

    // Основная информация
    @NotBlank(message = "Email обязателен")
    @Email(message = "Неверный формат email")
    private String email;

    private String firstName;
    private String lastName;

    @Pattern(regexp ="^\\+?[78][\\s\\-]*\\d{3}[\\s\\-]*\\d{3}[\\s\\-]*\\d{2}[\\s\\-]*\\d{2}$",
            message = "Неверный формат телефона")
    private String phone;

    // Поля для смены пароля (не обязательные)
    private String currentPassword;

    private String newPassword;

    private String confirmPassword;

    // Вспомогательные методы
    public boolean isPasswordChangeRequested() {
        return hasText(currentPassword)
                || hasText(newPassword)
                || hasText(confirmPassword);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    @AssertTrue(message = "Пароли не совпадают")
    public boolean isPasswordsMatch() {
        if (!hasText(newPassword) && !hasText(confirmPassword)) {
            return true; // пароль не меняем
        }
        return newPassword != null && newPassword.equals(confirmPassword);
    }
}
