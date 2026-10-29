package com.routeoptimizer.security;

import com.routeoptimizer.entity.User;
import com.routeoptimizer.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        String trimmedIdentifier = identifier != null ? identifier.trim() : "";
        User user = userRepository.findByUsernameIgnoreCase(trimmedIdentifier)
                .orElseGet(() -> userRepository.findByEmailIgnoreCase(trimmedIdentifier)
                        .orElseThrow(() -> new UsernameNotFoundException("User not found with username/email: " + identifier)));

        String password = user.getPassword() != null ? user.getPassword() : "";
        String roleName = user.getRole() != null ? user.getRole().name() : "USER";

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(password)
                .disabled(!Boolean.TRUE.equals(user.getActive()))
                .accountLocked(Boolean.TRUE.equals(user.getIsBlocked()))
                .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + roleName)))
                .build();
    }
}
