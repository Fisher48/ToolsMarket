package ru.fisher.ToolsMarket.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import ru.fisher.ToolsMarket.models.User;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final UserService userService;

    public Optional<User> findCurrentUser(UserDetails userDetails) {
        if (userDetails == null) {
            return Optional.empty();
        }
        return userService.findByUsername(userDetails.getUsername());
    }

    public Optional<User> findCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails ud) {
            return findCurrentUser(ud);
        }
        return Optional.empty();
    }

    public Long currentUserId(UserDetails userDetails) {
        return findCurrentUser(userDetails).map(User::getId).orElse(null);
    }

    public Long currentUserId(Authentication authentication) {
        return findCurrentUser(authentication).map(User::getId).orElse(null);
    }
}
