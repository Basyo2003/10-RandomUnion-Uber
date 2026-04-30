package com.team10.uber.location.security.chain;

import org.springframework.stereotype.Component;

@Component
public class RoleAuthorizationHandler extends AbstractAuthHandler {

    @Override
    public AuthResult handle(AuthContext ctx) {
        String requiredRole = ctx.getRequiredRole();

        if (requiredRole == null || requiredRole.isBlank()) {
            return handleNext(ctx);
        }

        if (!requiredRole.equals(ctx.getRole())) {
            return AuthResult.failure(403, "Insufficient role");
        }

        return handleNext(ctx);
    }
}