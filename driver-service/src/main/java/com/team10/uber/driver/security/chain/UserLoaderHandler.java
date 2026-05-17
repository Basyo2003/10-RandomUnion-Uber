package com.team10.uber.driver.security.chain;

public class UserLoaderHandler extends AuthHandler {

    @Override
    public AuthContext handle(AuthContext ctx) {
        if (ctx.getUserId() == null || ctx.getUserEmail() == null) {
            ctx.fail(401, "JWT token is missing user information");
            return ctx;
        }
        return passToNext(ctx);
    }
}
