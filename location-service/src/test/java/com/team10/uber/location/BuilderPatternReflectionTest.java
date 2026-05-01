package com.team10.uber.location;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

class BuilderPatternReflectionTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "com.team10.uber.location.dto.NearbyDriverDTO",
            "com.team10.uber.location.dto.LocationAnalyticsDTO",
            "com.team10.uber.location.dto.DriverMovementSummaryDTO"
    })
    void dto_hasBuilderStaticMethod(String className) throws Exception {
        Class<?> dtoClass = Class.forName(className);
        Method builderMethod = dtoClass.getMethod("builder");
        assertTrue(Modifier.isStatic(builderMethod.getModifiers()),
                className + " must have a static builder() method");
        assertNotNull(builderMethod.getReturnType(), "builder() must return a Builder type");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "com.team10.uber.location.dto.NearbyDriverDTO",
            "com.team10.uber.location.dto.LocationAnalyticsDTO",
            "com.team10.uber.location.dto.DriverMovementSummaryDTO"
    })
    void dto_builderHasBuildMethod(String className) throws Exception {
        Class<?> dtoClass = Class.forName(className);
        Method builderMethod = dtoClass.getMethod("builder");
        Class<?> builderClass = builderMethod.getReturnType();

        Method buildMethod = builderClass.getMethod("build");
        assertNotNull(buildMethod, "Builder class must have a build() method");
        assertEquals(dtoClass, buildMethod.getReturnType(),
                "build() must return an instance of " + className);
    }

    @Test
    void nearbyDriverDTO_builder_createsCorrectObject() throws Exception {
        Class<?> dtoClass = Class.forName("com.team10.uber.location.dto.NearbyDriverDTO");
        Method builderMethod = dtoClass.getMethod("builder");
        Object builder = builderMethod.invoke(null);

        Method buildMethod = builder.getClass().getMethod("build");
        Object result = buildMethod.invoke(builder);
        assertNotNull(result, "builder().build() must return a non-null object");
        assertInstanceOf(dtoClass, result, "builder().build() must return a NearbyDriverDTO");
    }
}
