package com.team10.uber.user;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

class ChainOfResponsibilityReflectionTest {

    private static final String PKG = "com.team10.uber.user.security.chain";

    @Test
    void authHandler_isAbstractClass_withSetNextAndHandleMethods() throws Exception {
        Class<?> authHandler = Class.forName(PKG + ".AuthHandler");
        assertTrue(Modifier.isAbstract(authHandler.getModifiers()), "AuthHandler must be abstract");

        Method setNext = authHandler.getDeclaredMethod("setNext", authHandler);
        assertNotNull(setNext, "AuthHandler must have setNext(AuthHandler) method");

        Class<?> authContext = Class.forName(PKG + ".AuthContext");
        Method handle = authHandler.getDeclaredMethod("handle", authContext);
        assertNotNull(handle, "AuthHandler must have handle(AuthContext) method");
        assertTrue(Modifier.isAbstract(handle.getModifiers()), "handle() must be abstract");
    }

    @Test
    void atLeastThreeConcreteHandlers_extendAuthHandler() throws Exception {
        Class<?> authHandler = Class.forName(PKG + ".AuthHandler");
        String[] handlerNames = {
                PKG + ".TokenExtractionHandler",
                PKG + ".SignatureValidationHandler",
                PKG + ".UserLoaderHandler",
                PKG + ".RoleAuthorizationHandler"
        };

        int count = 0;
        for (String name : handlerNames) {
            try {
                Class<?> handler = Class.forName(name);
                if (authHandler.isAssignableFrom(handler) && !Modifier.isAbstract(handler.getModifiers())) {
                    count++;
                }
            } catch (ClassNotFoundException ignored) {}
        }
        assertTrue(count >= 3, "Expected at least 3 concrete AuthHandler subclasses, found: " + count);
    }

    @Test
    void tokenExtractionHandler_extendsAuthHandler() throws Exception {
        Class<?> authHandler = Class.forName(PKG + ".AuthHandler");
        Class<?> handler = Class.forName(PKG + ".TokenExtractionHandler");
        assertTrue(authHandler.isAssignableFrom(handler), "TokenExtractionHandler must extend AuthHandler");
    }

    @Test
    void signatureValidationHandler_extendsAuthHandler() throws Exception {
        Class<?> authHandler = Class.forName(PKG + ".AuthHandler");
        Class<?> handler = Class.forName(PKG + ".SignatureValidationHandler");
        assertTrue(authHandler.isAssignableFrom(handler), "SignatureValidationHandler must extend AuthHandler");
    }

    @Test
    void roleAuthorizationHandler_extendsAuthHandler() throws Exception {
        Class<?> authHandler = Class.forName(PKG + ".AuthHandler");
        Class<?> handler = Class.forName(PKG + ".RoleAuthorizationHandler");
        assertTrue(authHandler.isAssignableFrom(handler), "RoleAuthorizationHandler must extend AuthHandler");
    }
}
