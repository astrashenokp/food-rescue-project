package com.example.foodrescue.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Date;

@Service
public class JwtValidatorService {

    private final JwtParser parser;

    public JwtValidatorService(@Value("${security.jwt.secret}") String secret, Clock clock) {
        this.parser = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .clock(() -> Date.from(clock.instant()))
                .build();
    }

    public Claims extractAllClaims(String token) {
        return parser.parseSignedClaims(token).getPayload();
    }

    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }
}
