package com.team10.uber.location;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

class ChainOfResponsibilityReflectionTest {

    private static final String PKG = "com.team10.uber.location.security.chain";

    @Test
    void authHandler_isInterface_withSetNextAndHandleMethods() throws Exception {
        Class<?> authHandler = Class.forName(PKG + ".AuthHandler");
        assertTrue(authHandler.isInterface(), "AuthHandler must be an interface");

        Method setNext = authHandler.getMethod("setNext", authHandler);
        assertNotNull(setNext, "AuthHandler must have setNext(AuthHandler) method");

        Class<?> authContext = Class.forName(PKG + ".AuthContext");
        Method handle = authHandler.getMethod("handle", authContext);
        assertNotNull(handle, "AuthHandler must have handle(AuthContext) method");
    }

    @Test
    void abstractAuthHandler_implementsAuthHandler() throws Exception {
        Class<?> authHandler = Class.forName(PKG + ".AuthHandler");
        Class<?> abstractHandler = Class.forName(PKG + ".AbstractAuthHandler");
        assertTrue(authHandler.isAssignableFrom(abstractHandler),
                "AbstractAuthHandler must implement AuthHandler");
        assertTrue(Modifier.isAbstract(abstractHandler.getModifiers()),
                "AbstractAuthHandler must be abstract");
    }

    @Test
    void atLeastThreeConcreteHandlers_extendAbstractAuthHandler() throws Exception {
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
        assertTrue(count >= 3, "Expected at least 3 concrete AuthHandler implementations, found: " + count);
    }

    @Test
    void tokenExtractionHandler_implementsAuthHandler() throws Exception {
        Class<?> authHandler = Class.forName(PKG + ".AuthHandler");
        Class<?> handler = Class.forName(PKG + ".TokenExtractionHandler");
        assertTrue(authHandler.isAssignableFrom(handler), "TokenExtractionHandler must implement AuthHandler");
    }

    @Test
    void signatureValidationHandler_implementsAuthHandler() throws Exception {
        Class<?> authHandler = Class.forName(PKG + ".AuthHandler");
        Class<?> handler = Class.forName(PKG + ".SignatureValidationHandler");
        assertTrue(authHandler.isAssignableFrom(handler), "SignatureValidationHandler must implement AuthHandler");
    }

    @Test
    void roleAuthorizationHandler_implementsAuthHandler() throws Exception {
        Class<?> authHandler = Class.forName(PKG + ".AuthHandler");
        Class<?> handler = Class.forName(PKG + ".RoleAuthorizationHandler");
        assertTrue(authHandler.isAssignableFrom(handler), "RoleAuthorizationHandler must implement AuthHandler");
    }
}
