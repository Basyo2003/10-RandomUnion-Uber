package com.team10.uber.location.security.chain;

public interface AuthHandler {

    AuthHandler setNext(AuthHandler next);

    AuthResult handle(AuthContext ctx);
}