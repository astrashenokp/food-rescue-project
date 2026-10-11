package com.example.foodrescue.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

import java.io.IOException;

public class JwtLoginFilter extends AbstractAuthenticationProcessingFilter {

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;
    private final AuthenticationEntryPoint authenticationEntryPoint;

    public JwtLoginFilter(
            String loginPath,
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            ObjectMapper objectMapper,
            AuthenticationEntryPoint authenticationEntryPoint) {
        super(PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, loginPath), authenticationManager);
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response)
            throws AuthenticationException, IOException {
        LoginRequest login;
        try {
            login = objectMapper.readValue(request.getInputStream(), LoginRequest.class);
        } catch (IOException ex) {
            throw new BadCredentialsException("Некоректне тіло запиту", ex);
        }
        if (login == null || login.username() == null || login.password() == null) {
            throw new BadCredentialsException("Не вказано логін або пароль");
        }
        return getAuthenticationManager().authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(login.username(), login.password()));
    }

    @Override
    protected void successfulAuthentication(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain,
            Authentication authResult) throws IOException {
        String token = jwtService.generateToken((UserDetails) authResult.getPrincipal());
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(),
                new AuthResponse(token, "Bearer", jwtService.getExpirationMillis() / 1000));
    }

    @Override
    protected void unsuccessfulAuthentication(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException failed) throws IOException, ServletException {
        authenticationEntryPoint.commence(request, response, failed);
    }
}
