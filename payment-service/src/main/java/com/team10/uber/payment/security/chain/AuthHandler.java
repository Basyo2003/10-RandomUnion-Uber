package com.team10.uber.payment.security.chain;

public abstract class AuthHandler {

    private AuthHandler next;

    public AuthHandler setNext(AuthHandler next) {
        this.next = next;
        return next;
    }

    public abstract AuthContext handle(AuthContext ctx);

    protected AuthContext passToNext(AuthContext ctx) {
        if (next != null) {
            return next.handle(ctx);
        }
        ctx.setPassed(true);
        return ctx;
    }
}
