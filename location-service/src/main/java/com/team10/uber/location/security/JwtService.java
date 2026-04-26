package com.team10.uber.location.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String jwtSecret;

    // 5.2 JWT Token - build signing key from shared secret
    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    // 5.2 JWT Token - extract token payload claims
    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // 5.2 JWT Token - validate signature, expiration, and required claims
    public boolean isTokenValid(String token) {
        try {
            Claims claims = extractAllClaims(token);

            return claims.getSubject() != null
                    && claims.get("uid") != null
                    && claims.get("role") != null;
        } catch (Exception e) {
            return false;
        }
    }
}