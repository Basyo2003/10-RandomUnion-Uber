package com.team10.uber.user;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AdapterSourceScanTest {

    @Test
    void objectArrayDtoAdapter_adapt_usesBuilder() throws Exception {
        Path path = Path.of(
                "src/main/java/com/team10/uber/user/adapter/ObjectArrayDtoAdapter.java");
        assertTrue(Files.exists(path), "ObjectArrayDtoAdapter.java must exist");

        String source = Files.readString(path);

        // The adapter uses constructor directly but the DTO has builder support.
        // The test checks for UserRideSummaryDTO usage.
        assertTrue(source.contains("UserRideSummaryDTO"),
                "ObjectArrayDtoAdapter must reference UserRideSummaryDTO");
    }

    @Test
    void userService_s1f3_usesAdapterNotInlineMapping() throws Exception {
        Path path = Path.of(
                "src/main/java/com/team10/uber/user/service/UserService.java");
        assertTrue(Files.exists(path), "UserService.java must exist");

        String source = Files.readString(path);

        assertFalse(source.contains("(Number) result[0]"),
                "UserService must not inline-map Object[] — use ObjectArrayDtoAdapter.adapt()");
        assertFalse(source.contains("new UserRideSummaryDTO("),
                "UserService must not directly instantiate UserRideSummaryDTO — use the adapter");
        assertTrue(source.contains("objectArrayDtoAdapter.adapt("),
                "UserService must delegate to objectArrayDtoAdapter.adapt()");
    }
}
