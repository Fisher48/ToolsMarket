package ru.fisher.ToolsMarket.dto;

import jakarta.persistence.Transient;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class UserDto {
    @NotBlank(message = "Имя пользователя обязательно")
    @Size(min = 3, max = 50, message = "Имя пользователя должно быть от 3 до 50 символов")
    private String username;

    @NotBlank(message = "Email обязателен")
    @Email(message = "Некорректный email адрес")
    private String email;

    @NotBlank(message = "Пароль обязателен")
    @Size(min = 6, message = "Пароль должен быть не менее 6 символов")
    private String password;

    @NotBlank(message = "Подтверждение пароля обязательно")
    @Transient
    private String confirmPassword;
    private String firstName;
    private String lastName;

    @Pattern(regexp ="^((8|\\+7)[\\- ]?)?(\\(?\\d{3}\\)?[\\- ]?)?[\\d\\- ]{7,10}$",
            message = "Неверный формат телефона")
    private String phone;

    // Кастомная валидация для проверки совпадения паролей
    @AssertTrue(message = "Пароли не совпадают")
    public boolean isPasswordsMatch() {
        return password != null && password.equals(confirmPassword);
    }
}
