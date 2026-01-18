package ru.fisher.ToolsMarket.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.fisher.ToolsMarket.dto.UserProfileUpdateDto;
import ru.fisher.ToolsMarket.models.Role;
import ru.fisher.ToolsMarket.models.User;
import ru.fisher.ToolsMarket.models.UserType;
import ru.fisher.ToolsMarket.repository.RoleRepository;
import ru.fisher.ToolsMarket.repository.UserRepository;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public List<User> findAll() {
        return userRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    @Transactional(readOnly = true)
    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<User> findByIdWithOrders(Long id) {
        return userRepository.findByIdWithOrders(id);
    }

    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    @Transactional
    public User registerUser(String username, String email, String password,
                             String firstName, String lastName) {

        if (existsByUsername(username)) {
            throw new IllegalArgumentException("Пользователь с таким именем уже существует");
        }

        if (existsByEmail(email)) {
            throw new IllegalArgumentException("Пользователь с таким email уже существует");
        }

        User user = User.builder()
                .username(username)
                .email(email)
                .password(passwordEncoder.encode(password))
                .firstName(firstName)
                .lastName(lastName)
                .enabled(true)
                .roles(new HashSet<>())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        // Добавляем роль USER по умолчанию
        Role userRole = roleRepository.findByName(Role.ROLE_USER)
                .orElseGet(() -> createRole(Role.ROLE_USER, "Обычный пользователь"));

        user.getRoles().add(userRole);

        User saved = userRepository.save(user);
        log.info("Пользователь зарегистрирован: {}", username);

        return saved;
    }

    public User createAdminUser(String username, String email, String password) {
        User admin = registerUser(username, email, password, "Admin", "Admin");

        Role adminRole = roleRepository.findByName(Role.ROLE_ADMIN)
                .orElseGet(() -> createRole(Role.ROLE_ADMIN, "Администратор"));

        admin.getRoles().add(adminRole);

        return userRepository.save(admin);
    }

    /**
     * Обновление пользователя (админка)
     */
    @Transactional
    public void updateUser(Long userId,
                           String firstName,
                           String lastName,
                           String email,
                           String phone,
                           UserType userType,
                           boolean enabled) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        // Проверяем уникальность email
        if (email != null && !email.equals(user.getEmail()) &&
                userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email уже используется другим пользователем");
        }

        if (firstName != null) user.setFirstName(firstName);
        if (lastName != null) user.setLastName(lastName);
        if (email != null) user.setEmail(email);
        if (phone != null) user.setPhone(phone);
        if (userType != null) user.setUserType(userType);
        user.setEnabled(enabled);
        user.setUpdatedAt(Instant.now());

        userRepository.save(user);

        log.info("Пользователь {} обновлен администратором", user.getUsername());
    }

    @Transactional
    public void updateUser(Long userId, String email, String firstName,
                           String lastName, String phone,
                           String currentPassword, String newPassword) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        // Обновление email
        if (email != null && !email.equals(user.getEmail())) {
            if (existsByEmail(email)) {
                throw new IllegalArgumentException("Email уже используется");
            }
            user.setEmail(email);
        }

        // Обновление остальных полей
        if (firstName != null) user.setFirstName(firstName);
        if (lastName != null) user.setLastName(lastName);
        if (phone != null) user.setPhone(phone);

        // Смена пароля, если указана
        if (currentPassword != null && newPassword != null &&
                !currentPassword.isEmpty() && !newPassword.isEmpty()) {

            changePassword(userId, currentPassword, newPassword);
            log.info("Пароль пользователя {} изменен", user.getUsername());
        }

        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
        log.info("Профиль пользователя {} обновлен", user.getUsername());
    }

    @Transactional
    public void updateProfile(Long userId, UserProfileUpdateDto dto) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        // ===== ОБНОВЛЕНИЕ ПРОФИЛЯ =====

        if (!user.getEmail().equals(dto.getEmail()) &&
                userRepository.existsByEmail(dto.getEmail())) {
            throw new IllegalArgumentException("Email уже используется");
        }

        user.setEmail(dto.getEmail());
        user.setFirstName(dto.getFirstName());
        user.setLastName(dto.getLastName());
        user.setPhone(dto.getPhone());

        // ===== СМЕНА ПАРОЛЯ =====
        if (dto.isPasswordChangeRequested()) {
            changePassword(userId, dto.getCurrentPassword(), dto.getNewPassword());
        }

        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        log.info("Профиль пользователя {} обновлен", user.getUsername());
    }

    @Transactional
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new IllegalArgumentException("Неверный текущий пароль");
        }

        if (oldPassword.equals(newPassword)) {
            throw new IllegalArgumentException("Новый пароль должен отличаться от старого");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(Instant.now());

        userRepository.save(user);
        log.info("Пароль пользователя {} изменен", user.getUsername());
    }

    /**
     * Смена пароля пользователя администратором
     */
    @Transactional
    public void changePasswordByAdmin(Long userId, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(Instant.now());

        userRepository.save(user);

        log.info("Пароль пользователя {} изменен администратором", user.getUsername());
    }

    private Role createRole(String name, String description) {
        Role role = Role.builder()
                .name(name)
                .description(description)
                .build();

        return roleRepository.save(role);
    }

    @Transactional(readOnly = true)
    public Set<Role> getUserRoles(Long userId) {
        return userRepository.findById(userId)
                .map(User::getRoles)
                .orElse(Set.of());
    }

    public void addRoleToUser(Long userId, String roleName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalArgumentException("Роль не найдена"));

        user.getRoles().add(role);
        userRepository.save(user);
    }

    public void removeRoleFromUser(Long userId, String roleName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        user.getRoles().removeIf(role -> role.getName().equals(roleName));
        userRepository.save(user);
    }

    /**
     * Сохранение пользователя
     */
    @Transactional
    public User save(User user) {
        if (user.getId() == null) {
            user.setCreatedAt(Instant.now());
        }
        user.setUpdatedAt(Instant.now());
        return userRepository.save(user);
    }

    /**
     * Поиск пользователя по email
     */
    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    /**
     * Изменение статуса пользователя
     */
    @Transactional
    public void toggleUserStatus(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        user.setEnabled(!user.isEnabled());
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        log.info("Статус пользователя {} изменен на: {}",
                user.getUsername(), user.isEnabled() ? "активен" : "неактивен");
    }

    /**
     * Изменение типа пользователя для скидок
     */
    @Transactional
    public void changeUserType(Long userId, UserType userType) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        user.setUserType(userType);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        log.info("Тип пользователя {} изменен на: {}",
                user.getUsername(), userType.getDisplayName());
    }

    /**
     * Получение всех пользователей с пагинацией
     */
    @Transactional(readOnly = true)
    public Page<User> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable);
    }

    /**
     * Поиск пользователей
     */
    @Transactional(readOnly = true)
    public Page<User> searchUsers(String query, Pageable pageable) {
        return userRepository.searchUsers(query, pageable);
    }

    /**
     * Получение количества пользователей
     */
    @Transactional(readOnly = true)
    public long countUsers() {
        return userRepository.count();
    }

    /**
     * Получение количества активных пользователей
     */
    @Transactional(readOnly = true)
    public long countActiveUsers() {
        return userRepository.countByEnabledTrue();
    }

    /**
     * Получение статистики по типам пользователей
     */
    @Transactional(readOnly = true)
    public Map<UserType, Long> getUserTypeStatistics() {
        List<User> allUsers = userRepository.findAll();
        return allUsers.stream()
                .collect(Collectors.groupingBy(
                        User::getUserType,
                        Collectors.counting()
                ));
    }

    /**
     * Получение пользователей по типу
     */
    @Transactional(readOnly = true)
    public List<User> getUsersByType(UserType userType) {
        return userRepository.findByUserType(userType);
    }

    /**
     * Блокировка пользователя
     */
    @Transactional
    public void disableUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        user.setEnabled(false);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        log.info("Пользователь {} заблокирован", user.getUsername());
    }

    /**
     * Разблокировка пользователя
     */
    @Transactional
    public void enableUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        user.setEnabled(true);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        log.info("Пользователь {} разблокирован", user.getUsername());
    }

    /**
     * Удаление пользователя (только если нет заказов)
     */
    @Transactional
    public void deleteUser(Long userId) {
        User user = userRepository.findByIdWithOrders(userId)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        // Проверяем, есть ли у пользователя заказы
        if (user.getOrders() != null && !user.getOrders().isEmpty()) {
            throw new IllegalStateException("Нельзя удалить пользователя с заказами");
        }

        userRepository.delete(user);

        log.info("Пользователь {} удален", user.getUsername());
    }

}
