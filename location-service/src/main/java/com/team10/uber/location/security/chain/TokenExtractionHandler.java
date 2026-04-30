package com.team10.uber.location.security.chain;

import org.springframework.stereotype.Component;

@Component
public class TokenExtractionHandler extends AbstractAuthHandler {

    @Override
    public AuthResult handle(AuthContext ctx) {
        String authHeader = ctx.getRequest().getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return AuthResult.failure(401, "Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7);
        ctx.setToken(token);

        return handleNext(ctx);
    }
}