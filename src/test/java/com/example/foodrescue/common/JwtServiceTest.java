package com.example.foodrescue.common;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-32";

    private final AppUser donor = new AppUser(UUID.randomUUID(), "donor1", "hash", AppRole.DONOR);

    @Test
    void generatedToken_containsUsernameAndRoles() {
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC);
        JwtService jwtService = new JwtService(SECRET, 15, clock);
        JwtValidatorService validator = new JwtValidatorService(SECRET, clock);

        String token = jwtService.generateToken(donor);

        assertThat(validator.extractUsername(token)).isEqualTo("donor1");
        assertThat(validator.extractAllClaims(token).get("roles", List.class)).containsExactly("ROLE_DONOR");
    }

    @Test
    void expiredToken_isRejected() {
        Instant issuedAt = Instant.parse("2026-01-01T10:00:00Z");
        JwtService jwtService = new JwtService(SECRET, 15, Clock.fixed(issuedAt, ZoneOffset.UTC));
        JwtValidatorService validator = new JwtValidatorService(
                SECRET, Clock.fixed(issuedAt.plusSeconds(16 * 60), ZoneOffset.UTC));

        String token = jwtService.generateToken(donor);

        assertThatThrownBy(() -> validator.extractUsername(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void tokenSignedWithOtherSecret_isRejected() {
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC);
        JwtService other = new JwtService("another-secret-another-secret-another-32", 15, clock);
        JwtValidatorService validator = new JwtValidatorService(SECRET, clock);

        String token = other.generateToken(donor);

        assertThatThrownBy(() -> validator.extractUsername(token)).isInstanceOf(JwtException.class);
    }
}
