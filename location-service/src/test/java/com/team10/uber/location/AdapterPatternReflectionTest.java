package com.team10.uber.location;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class AdapterPatternReflectionTest {

    @Test
    void noSqlAdapter_isInterface_withAdaptMethod() throws Exception {
        Class<?> adapterInterface = Class.forName("com.team10.uber.location.adapter.NoSqlAdapter");
        assertTrue(adapterInterface.isInterface(), "NoSqlAdapter must be an interface");
        boolean hasAdapt = Arrays.stream(adapterInterface.getMethods())
                .anyMatch(m -> m.getName().equals("adapt"));
        assertTrue(hasAdapt, "NoSqlAdapter must declare an adapt() method");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "com.team10.uber.location.adapter.MongoDocumentAdapter",
            "com.team10.uber.location.adapter.CassandraRowAdapter"
    })
    void adapterClass_hasAdaptMethod(String className) throws Exception {
        Class<?> adapterClass = Class.forName(className);
        boolean hasAdapt = Arrays.stream(adapterClass.getMethods())
                .anyMatch(m -> m.getName().equals("adapt"));
        assertTrue(hasAdapt, className + " must have an adapt() method");
    }

    @Test
    void mongoDocumentAdapter_hasAdaptMethod() throws Exception {
        Class<?> mongoAdapter = Class.forName("com.team10.uber.location.adapter.MongoDocumentAdapter");
        boolean hasAdapt = Arrays.stream(mongoAdapter.getMethods())
                .anyMatch(m -> m.getName().equals("adapt"));
        assertTrue(hasAdapt, "MongoDocumentAdapter must have an adapt() method");
    }

    @Test
    void cassandraRowAdapter_hasAdaptMethod() throws Exception {
        Class<?> cassandraAdapter = Class.forName("com.team10.uber.location.adapter.CassandraRowAdapter");
        boolean hasAdapt = Arrays.stream(cassandraAdapter.getMethods())
                .anyMatch(m -> m.getName().equals("adapt"));
        assertTrue(hasAdapt, "CassandraRowAdapter must have an adapt() method");
    }
}
