package com.example.chatapp.user.util;

import com.example.chatapp.user.model.entity.Role;
import com.example.chatapp.user.model.entity.RoleName;
import com.example.chatapp.user.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.example.chatapp.user.model.entity.User;
import com.example.chatapp.user.model.entity.UserSettings;
import com.example.chatapp.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

@Component
@RequiredArgsConstructor
@Order(1)
@Slf4j
public class RoleDataInitiator implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        Role adminRole = roleRepository.findByName(RoleName.ROLE_ADMIN).orElse(new Role());
        Role userRole = roleRepository.findByName(RoleName.ROLE_USER).orElse(new Role());

        if (adminRole.getName() == null) {
            adminRole.setName(RoleName.ROLE_ADMIN);
            adminRole = roleRepository.save(adminRole);
            log.info("Admin role created.");
        } else {
            log.info("Admin role already exists.");
        }
        if (userRole.getName() == null) {
            userRole.setName(RoleName.ROLE_USER);
            userRole = roleRepository.save(userRole);
            log.info("User role created.");
        } else {
            log.info("User role already exists.");
        }

        // Initialize system user
        if (userRepository.findByUsername("system").isEmpty()) {
            User systemUser = User.builder()
                    .username("system")
                    .password(passwordEncoder.encode("system_pass_secured_12345"))
                    .role(adminRole.getId() != null ? adminRole : userRole)
                    .version(0L)
                    .build();
            UserSettings settings = UserSettings.builder()
                    .user(systemUser)
                    .sharePresence(true)
                    .build();
            systemUser.setSettings(settings);
            userRepository.save(systemUser);
            log.info("System user created.");
        }
    }
}
