package com.example.foodrescue.common;

import com.example.foodrescue.security.user.AppRole;
import com.example.foodrescue.security.user.AppUser;
import com.example.foodrescue.security.user.JpaUserDetailsService;
import com.example.foodrescue.security.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class JpaUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private JpaUserDetailsService service;

    @Test
    void existingUser_isReturnedWithRoleAuthority() {
        AppUser admin = new AppUser(UUID.randomUUID(), "admin1", "hash", AppRole.ADMIN);
        given(userRepository.findByUsername("admin1")).willReturn(Optional.of(admin));

        UserDetails result = service.loadUserByUsername("admin1");

        assertThat(result.getUsername()).isEqualTo("admin1");
        assertThat(result.getAuthorities()).extracting("authority").containsExactly("ROLE_ADMIN");
    }

    @Test
    void unknownUser_throwsUsernameNotFound() {
        given(userRepository.findByUsername("ghost")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("ghost"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
