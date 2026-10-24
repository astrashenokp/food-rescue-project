package com.example.foodrescue.security.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.example.foodrescue.common.DemoData;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@EnableConfigurationProperties(DemoProperties.class)
public class DemoUsers implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoUsers.class);

    public static final UUID DONOR_USER = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
    public static final UUID VOLUNTEER_USER = UUID.fromString("00000000-0000-0000-0000-0000000000e2");
    public static final UUID COORDINATOR_USER = UUID.fromString("00000000-0000-0000-0000-0000000000e3");
    public static final UUID ADMIN_USER = UUID.fromString("00000000-0000-0000-0000-0000000000e4");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final DemoProperties demoProperties;

    public DemoUsers(UserRepository userRepository, PasswordEncoder passwordEncoder, DemoProperties demoProperties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.demoProperties = demoProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        create(DONOR_USER, "donor1", AppRole.DONOR, DemoData.DONOR);
        create(VOLUNTEER_USER, "volunteer1", AppRole.VOLUNTEER, DemoData.DEMO_VOLUNTEER);
        create(COORDINATOR_USER, "coordinator1", AppRole.COORDINATOR, null);
        create(ADMIN_USER, "admin1", AppRole.ADMIN, null);
        log.info("Демо-користувачів створено");
    }

    private void create(UUID id, String username, AppRole role, UUID profileId) {
        if (!userRepository.existsByUsername(username)) {
            String password = demoProperties.passwords().get(username);
            userRepository.save(new AppUser(id, username, passwordEncoder.encode(password), role, profileId));
        }
    }
}
