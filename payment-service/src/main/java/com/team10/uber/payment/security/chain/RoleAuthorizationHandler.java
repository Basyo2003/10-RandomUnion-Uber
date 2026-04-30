package com.team10.uber.payment.security.chain;

import java.util.Map;

public class RoleAuthorizationHandler extends AuthHandler {

    private static final Map<String, String> ROLE_REQUIREMENTS = Map.of(
            "PUT:/api/users/*/role", "ADMIN"
    );

    @Override
    public AuthContext handle(AuthContext ctx) {
        String method   = ctx.getRequest().getMethod();
        String servletPath = ctx.getRequest().getServletPath();
        String path = (servletPath == null || servletPath.isEmpty())
                ? ctx.getRequest().getRequestURI()
                : servletPath;
        String userRole = ctx.getRole();

        for (Map.Entry<String, String> entry : ROLE_REQUIREMENTS.entrySet()) {
            if (matchesPattern(method, path, entry.getKey())) {
                if (!entry.getValue().equals(userRole)) {
                    ctx.fail(403, "Insufficient role: requires " + entry.getValue());
                    return ctx;
                }
            }
        }

        return passToNext(ctx);
    }

    private boolean matchesPattern(String method, String path, String pattern) {
        String[] parts = pattern.split(":", 2);
        if (parts.length != 2) return false;
        if (!parts[0].equalsIgnoreCase(method)) return false;
        String regex = parts[1].replace("*", "[^/]+");
        return path.matches(regex);
    }
}
