package com.team10.uber.location.security.chain;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class UserLoaderHandler extends AbstractAuthHandler {

    private final JdbcTemplate jdbcTemplate;

    public UserLoaderHandler(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public AuthResult handle(AuthContext ctx) {
        if (ctx.getUserId() == null || ctx.getEmail() == null) {
            return AuthResult.failure(401, "JWT token is missing user information");
        }

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE id = ? AND email = ?",
                Integer.class,
                ctx.getUserId(),
                ctx.getEmail()
        );

        if (count == null || count == 0) {
            return AuthResult.failure(401, "User not found");
        }

        return handleNext(ctx);
    }
}