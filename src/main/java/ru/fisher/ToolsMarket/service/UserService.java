package ru.fisher.ToolsMarket.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.fisher.ToolsMarket.dto.UserProfileDto;
import ru.fisher.ToolsMarket.dto.UserProfileUpdateDto;
import ru.fisher.ToolsMarket.models.Role;
import ru.fisher.ToolsMarket.models.User;
import ru.fisher.ToolsMarket.repository.RoleRepository;
import ru.fisher.ToolsMarket.repository.UserRepository;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
    public void updateUserProfile(Long userId, UserProfileDto profileDto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        // Проверяем email на уникальность
        if (!user.getEmail().equals(profileDto.getEmail()) &&
                userRepository.existsByEmail(profileDto.getEmail())) {
            throw new IllegalArgumentException("Пользователь с таким email уже существует");
        }

        user.setFirstName(profileDto.getFirstName());
        user.setLastName(profileDto.getLastName());
        user.setEmail(profileDto.getEmail());
        user.setPhone(profileDto.getPhone());
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

}
