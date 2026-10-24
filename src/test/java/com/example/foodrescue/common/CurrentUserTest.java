package com.example.foodrescue.common;

import com.example.foodrescue.security.user.AppRole;
import com.example.foodrescue.security.user.AppUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CurrentUserTest {

    private final CurrentUser currentUser = new CurrentUser();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void profileId_UserWithProfile_ReturnsProfileId() {
        UUID profileId = UUID.randomUUID();
        AppUser user = new AppUser(UUID.randomUUID(), "volunteer1", "hash", AppRole.VOLUNTEER, profileId);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));

        assertThat(currentUser.profileId()).isEqualTo(profileId);
    }

    @Test
    void profileId_UserWithoutProfile_ReturnsNull() {
        AppUser user = new AppUser(UUID.randomUUID(), "admin1", "hash", AppRole.ADMIN);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));

        assertThat(currentUser.profileId()).isNull();
    }

    @Test
    void profileId_PrincipalWithoutProfile_ReturnsNull() {
        User principal = new User("someone", "x", AuthorityUtils.createAuthorityList("ROLE_DONOR"));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));

        assertThat(currentUser.profileId()).isNull();
    }

    @Test
    void profileId_NoAuthentication_ReturnsNull() {
        assertThat(currentUser.profileId()).isNull();
    }
}
