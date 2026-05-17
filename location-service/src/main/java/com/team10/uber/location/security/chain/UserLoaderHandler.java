package com.team10.uber.location.security.chain;

import org.springframework.stereotype.Component;

@Component
public class UserLoaderHandler extends AbstractAuthHandler {

    @Override
    public AuthResult handle(AuthContext ctx) {
        if (ctx.getUserId() == null || ctx.getEmail() == null) {
            return AuthResult.failure(401, "JWT token is missing user information");
        }
        return handleNext(ctx);
    }
}