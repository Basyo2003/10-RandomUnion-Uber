package com.team10.uber.location.security.chain;

import com.team10.uber.location.security.JwtService;
import io.jsonwebtoken.Claims;
import org.springframework.stereotype.Component;

@Component
public class SignatureValidationHandler extends AbstractAuthHandler {

    private final JwtService jwtService;

    public SignatureValidationHandler(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public AuthResult handle(AuthContext ctx) {
        if (!jwtService.isTokenValid(ctx.getToken())) {
            return AuthResult.failure(401, "Invalid or expired JWT token");
        }

        Claims claims = jwtService.extractAllClaims(ctx.getToken());

        ctx.setClaims(claims);
        ctx.setEmail(claims.getSubject());
        ctx.setRole(claims.get("role", String.class));

        Object uid = claims.get("uid");

        if (uid instanceof Integer) {
            ctx.setUserId(((Integer) uid).longValue());
        } else if (uid instanceof Long) {
            ctx.setUserId((Long) uid);
        } else if (uid != null) {
            ctx.setUserId(Long.valueOf(uid.toString()));
        }

        return handleNext(ctx);
    }
}