package com.team10.uber.user.service;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class UserServiceObserverTest {

    @Test
    void userService_hasNoDirectMongoSave() throws Exception {
        String source = Files.readString(Path.of(
            "src/main/java/com/team10/uber/user/service/UserService.java"));
        assertFalse(source.contains("authEventRepository.save"),
            "UserService must not call authEventRepository.save() directly — use notifyObservers()");
        assertFalse(source.contains("logEvent("),
            "UserService must not use the logEvent() helper — it bypasses the Observer chain");
    }

    @Test
    void userService_hasNotifyObserversForAllM1Writes() throws Exception {
        String source = Files.readString(Path.of(
            "src/main/java/com/team10/uber/user/service/UserService.java"));
        assertTrue(source.contains("notifyObservers(\"USER_UPDATED\""),
            "updatePreferences() must call notifyObservers(USER_UPDATED, ...)");
        assertTrue(source.contains("notifyObservers(\"USER_DEACTIVATED\""),
            "deactivateUser() must call notifyObservers(USER_DEACTIVATED, ...)");
        assertTrue(source.contains("notifyObservers(\"DEFAULT_ADDRESS_SET\""),
            "setDefaultSavedAddress() must call notifyObservers(DEFAULT_ADDRESS_SET, ...)");
    }
}
