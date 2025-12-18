package ru.fisher.ToolsMarket.service;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.fisher.ToolsMarket.repository.UserRepository;

@Service
public class UserDetailServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    public UserDetailServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByUsername(username)
                .or(() -> userRepository.findByEmail(username)) // Можно логиниться и по email
                .orElseThrow(() -> new UsernameNotFoundException(
                        String.format("Пользователь '%s' не найден", username)
                ));
    }
}
