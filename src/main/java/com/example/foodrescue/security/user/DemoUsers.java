package com.example.foodrescue.common;

import com.example.foodrescue.security.user.AppRole;
import com.example.foodrescue.security.user.AppUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class DemoUsers implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoUsers.class);

    public static final UUID DONOR_USER = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
    public static final UUID VOLUNTEER_USER = UUID.fromString("00000000-0000-0000-0000-0000000000e2");
    public static final UUID COORDINATOR_USER = UUID.fromString("00000000-0000-0000-0000-0000000000e3");
    public static final UUID ADMIN_USER = UUID.fromString("00000000-0000-0000-0000-0000000000e4");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DemoUsers(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        create(DONOR_USER, "donor1", "donorpass1", AppRole.DONOR);
        create(VOLUNTEER_USER, "volunteer1", "volunteerpass1", AppRole.VOLUNTEER);
        create(COORDINATOR_USER, "coordinator1", "coordinatorpass1", AppRole.COORDINATOR);
        create(ADMIN_USER, "admin1", "adminpass1", AppRole.ADMIN);
        log.info("Демо-користувачів створено");
    }

    private void create(UUID id, String username, String password, AppRole role) {
        if (!userRepository.existsByUsername(username)) {
            userRepository.save(new AppUser(id, username, passwordEncoder.encode(password), role));
        }
    }
}
