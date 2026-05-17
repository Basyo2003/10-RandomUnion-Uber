package com.team10.uber.payment.security.chain;

import com.team10.uber.contracts.feign.UserServiceClient;
import feign.FeignException;

public class UserLoaderHandler extends AuthHandler {

    private final UserServiceClient userServiceClient;

    public UserLoaderHandler(UserServiceClient userServiceClient) {
        this.userServiceClient = userServiceClient;
    }

    @Override
    public AuthContext handle(AuthContext ctx) {
        Long userId = ctx.getUserId();
        String email = ctx.getUserEmail();

        if (userId == null || email == null) {
            ctx.fail(401, "JWT token is missing user information");
            return ctx;
        }

        try {
            userServiceClient.getUser(userId);
        } catch (FeignException.NotFound e) {
            ctx.fail(401, "User not found");
            return ctx;
        } catch (FeignException e) {
            ctx.fail(503, "User service unavailable");
            return ctx;
        }

        return passToNext(ctx);
    }
}
