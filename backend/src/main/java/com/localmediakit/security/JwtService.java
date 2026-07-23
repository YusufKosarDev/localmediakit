package com.localmediakit.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    /**
     * Non-session tokens carry a "typ" claim; session tokens never do. Each
     * extractor accepts exactly one shape, so a short-lived preview link can
     * never be replayed as a login session (and vice versa).
     */
    private static final String TYPE_CLAIM = "typ";
    private static final String PREVIEW_TYPE = "preview";
    private static final String KIT_ID_CLAIM = "kitId";

    private final SecretKey key;
    private final long expirationMillis;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-minutes}") long expirationMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMillis = expirationMinutes * 60_000L;
    }

    /** Issues a signed session token whose subject is the user's email. */
    public String generateToken(String subject) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subject)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMillis)))
                .signWith(key)
                .compact();
    }

    /** Issues a short-lived token that grants read access to ONE kit's draft preview. */
    public String generatePreviewToken(Long kitId, long ttlMinutes) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject("kit-" + kitId)
                .claim(TYPE_CLAIM, PREVIEW_TYPE)
                .claim(KIT_ID_CLAIM, kitId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(ttlMinutes * 60_000L)))
                .signWith(key)
                .compact();
    }

    /** @return the subject (email) if the token is a valid SESSION token; throws JwtException otherwise. */
    public String extractSubject(String token) {
        Claims claims = parse(token);
        if (claims.get(TYPE_CLAIM) != null) {
            throw new JwtException("Not a session token");
        }
        return claims.getSubject();
    }

    /** @return the kit id if the token is a valid PREVIEW token; throws JwtException otherwise. */
    public Long extractPreviewKitId(String token) {
        Claims claims = parse(token);
        if (!PREVIEW_TYPE.equals(claims.get(TYPE_CLAIM, String.class))) {
            throw new JwtException("Not a preview token");
        }
        return claims.get(KIT_ID_CLAIM, Long.class);
    }

    private Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
