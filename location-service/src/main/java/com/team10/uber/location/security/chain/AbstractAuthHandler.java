package com.team10.uber.location.security.chain;

public abstract class AbstractAuthHandler implements AuthHandler {

    private AuthHandler next;

    @Override
    public AuthHandler setNext(AuthHandler next) {
        this.next = next;
        return next;
    }

    protected AuthResult handleNext(AuthContext ctx) {
        if (next == null) {
            return AuthResult.success();
        }

        return next.handle(ctx);
    }
}