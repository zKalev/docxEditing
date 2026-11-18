package com.example.docs.service;

import com.example.docs.config.OnlyOfficeProperties;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * HS256 tokens shared with Document Server. Document Server (7.2+) enables JWT by default and rejects
 * unsigned editor configs, so the backend signs every config and verifies every callback.
 */
@Service
public class JwtService {

    private final OnlyOfficeProperties properties;
    private final SecretKey key;

    public JwtService(OnlyOfficeProperties properties) {
        this.properties = properties;
        this.key = properties.jwtEnabled()
                ? Keys.hmacShaKeyFor(properties.jwtSecret().getBytes(StandardCharsets.UTF_8))
                : null;
    }

    public boolean enabled() {
        return properties.jwtEnabled();
    }

    public String sign(Map<String, ?> claims) {
        // Force HS256: jjwt would otherwise pick HS384/HS512 for long secrets, which Document Server rejects by default.
        return Jwts.builder().claims(claims).signWith(key, Jwts.SIG.HS256).compact();
    }

    /** Returns the verified claims, or throws {@link JwtException} if the token is invalid. */
    public Map<String, Object> verify(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
