package com.team10.uber.driver.security.chain;

public class TokenExtractionHandler extends AuthHandler {

    @Override
    public AuthContext handle(AuthContext ctx) {
        String authHeader = ctx.getRequest().getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            ctx.fail(401, "Missing or malformed Authorization header");
            return ctx;
        }

        String token = authHeader.substring(7).trim();
        if (token.isEmpty()) {
            ctx.fail(401, "Empty token");
            return ctx;
        }

        ctx.setToken(token);
        return passToNext(ctx);
    }
}
