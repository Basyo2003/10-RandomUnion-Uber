package com.team10.uber.driver;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class AdapterPatternReflectionTest {

    @Test
    void noSqlAdapter_isInterface_withAdaptMethod() throws Exception {
        Class<?> adapterInterface = Class.forName("com.team10.uber.driver.adapter.NoSqlAdapter");
        assertTrue(adapterInterface.isInterface(), "NoSqlAdapter must be an interface");
        boolean hasAdapt = Arrays.stream(adapterInterface.getMethods())
                .anyMatch(m -> m.getName().equals("adapt"));
        assertTrue(hasAdapt, "NoSqlAdapter must declare an adapt() method");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "com.team10.uber.driver.adapter.MongoDocumentAdapter",
            "com.team10.uber.driver.adapter.ElasticsearchHitAdapter"
    })
    void adapterClass_hasAdaptMethod(String className) throws Exception {
        Class<?> adapterClass = Class.forName(className);
        boolean hasAdapt = Arrays.stream(adapterClass.getMethods())
                .anyMatch(m -> m.getName().equals("adapt"));
        assertTrue(hasAdapt, className + " must have an adapt() method");
    }

    @Test
    void mongoDocumentAdapter_implementsNoSqlAdapter() throws Exception {
        Class<?> noSqlAdapter = Class.forName("com.team10.uber.driver.adapter.NoSqlAdapter");
        Class<?> mongoAdapter = Class.forName("com.team10.uber.driver.adapter.MongoDocumentAdapter");
        assertTrue(noSqlAdapter.isAssignableFrom(mongoAdapter),
                "MongoDocumentAdapter must implement NoSqlAdapter");
    }

    @Test
    void elasticsearchHitAdapter_implementsNoSqlAdapter() throws Exception {
        Class<?> noSqlAdapter = Class.forName("com.team10.uber.driver.adapter.NoSqlAdapter");
        Class<?> esAdapter = Class.forName("com.team10.uber.driver.adapter.ElasticsearchHitAdapter");
        assertTrue(noSqlAdapter.isAssignableFrom(esAdapter),
                "ElasticsearchHitAdapter must implement NoSqlAdapter");
    }
}
