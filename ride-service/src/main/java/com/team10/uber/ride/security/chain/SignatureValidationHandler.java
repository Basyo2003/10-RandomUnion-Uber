package com.team10.uber.ride.security.chain;

import com.team10.uber.ride.singleton.JwtConfigurationManager;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;

public class SignatureValidationHandler extends AuthHandler {

    @Override
    public AuthContext handle(AuthContext ctx) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(JwtConfigurationManager.getInstance().getSecretKey())
                    .build()
                    .parseSignedClaims(ctx.getToken())
                    .getPayload();

            ctx.setClaims(claims);
            ctx.setUserEmail(claims.getSubject());

            Object uid = claims.get("uid");
            if (uid instanceof Integer) {
                ctx.setUserId(((Integer) uid).longValue());
            } else if (uid instanceof Long) {
                ctx.setUserId((Long) uid);
            } else if (uid != null) {
                ctx.setUserId(Long.valueOf(uid.toString()));
            }

            ctx.setRole(claims.get("role", String.class));

            return passToNext(ctx);

        } catch (ExpiredJwtException ex) {
            ctx.fail(401, "Token expired");
            return ctx;
        } catch (JwtException | IllegalArgumentException ex) {
            ctx.fail(401, "Invalid token signature");
            return ctx;
        }
    }
}
