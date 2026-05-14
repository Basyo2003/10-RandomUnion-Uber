package com.team10.uber.location.security;

import com.team10.uber.location.singleton.JwtConfigurationManager;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;

@Service
public class JwtService {

    // 3.6 Singleton - read JWT config from the single shared manager instance
    private SecretKey getSigningKey() {
        return JwtConfigurationManager.getInstance().getSecretKey();
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