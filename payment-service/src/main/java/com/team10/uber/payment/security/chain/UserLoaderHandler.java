package com.team10.uber.payment.security.chain;

import org.springframework.jdbc.core.JdbcTemplate;

public class UserLoaderHandler extends AuthHandler {

    private final JdbcTemplate jdbcTemplate;

    public UserLoaderHandler(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public AuthContext handle(AuthContext ctx) {
        Long userId = ctx.getUserId();
        String email = ctx.getUserEmail();

        if (userId == null || email == null) {
            ctx.fail(401, "JWT token is missing user information");
            return ctx;
        }

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE id = ? AND email = ?",
                Integer.class,
                userId,
                email
        );

        if (count == null || count == 0) {
            ctx.fail(401, "User not found");
            return ctx;
        }

        return passToNext(ctx);
    }
}
