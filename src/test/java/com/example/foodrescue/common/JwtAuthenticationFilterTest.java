package com.example.foodrescue.common;

import io.jsonwebtoken.MalformedJwtException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtValidatorService jwtValidatorService;

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private AuthenticationEntryPoint authenticationEntryPoint;

    @Mock
    private FilterChain filterChain;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private JwtAuthenticationFilter filter() {
        return new JwtAuthenticationFilter(jwtValidatorService, userDetailsService, authenticationEntryPoint);
    }

    @Test
    void validToken_setsAuthentication() throws Exception {
        AppUser donor = new AppUser(UUID.randomUUID(), "donor1", "hash", AppRole.DONOR);
        given(jwtValidatorService.extractUsername("good")).willReturn("donor1");
        given(userDetailsService.loadUserByUsername("donor1")).willReturn(donor);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer good");

        filter().doFilter(request, new MockHttpServletResponse(), filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("donor1");
        verify(filterChain).doFilter(any(), any());
    }

    @Test
    void invalidToken_callsEntryPointAndStopsChain() throws Exception {
        given(jwtValidatorService.extractUsername("bad")).willThrow(new MalformedJwtException("bad"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer bad");

        filter().doFilter(request, new MockHttpServletResponse(), filterChain);

        verify(authenticationEntryPoint).commence(any(), any(), any(AuthenticationException.class));
        verifyNoInteractions(filterChain);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void missingHeader_passesThroughWithoutAuthentication() throws Exception {
        filter().doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), filterChain);

        verify(filterChain).doFilter(any(), any());
        verifyNoInteractions(jwtValidatorService);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void disabledUser_callsEntryPointAndStopsChain() throws Exception {
        UserDetails disabled = mock(UserDetails.class);
        given(disabled.isEnabled()).willReturn(false);
        given(jwtValidatorService.extractUsername("good")).willReturn("donor1");
        given(userDetailsService.loadUserByUsername("donor1")).willReturn(disabled);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer good");

        filter().doFilter(request, new MockHttpServletResponse(), filterChain);

        verify(authenticationEntryPoint).commence(any(), any(), any(AuthenticationException.class));
        verifyNoInteractions(filterChain);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
