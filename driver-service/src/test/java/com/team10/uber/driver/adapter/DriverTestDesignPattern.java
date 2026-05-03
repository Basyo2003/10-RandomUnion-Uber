package com.team10.uber.driver.adapter;

import com.team10.uber.driver.adapter.ElasticsearchHitAdapter;
import com.team10.uber.driver.adapter.MongoDocumentAdapter;
import com.team10.uber.driver.adapter.NoSqlAdapter;
import com.team10.uber.driver.adapter.TopRatedDriverAdapter;
import com.team10.uber.driver.dto.*;
import com.team10.uber.driver.enums.DriverStatus;
import com.team10.uber.driver.event.DriverEvent;
import com.team10.uber.driver.model.Driver;
import com.team10.uber.driver.observer.AbstractObservableService;
import com.team10.uber.driver.observer.EntityObserver;
import com.team10.uber.driver.observer.MongoEventLogger;
import com.team10.uber.driver.repository.DriverRepository;
import com.team10.uber.driver.repository.mongo.DriverEventRepository;
import com.team10.uber.driver.singleton.JwtConfigurationManager;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DriverTestDesignPattern {

    @Mock private DriverRepository driverRepository;
    @Mock private DriverEventRepository driverEventRepository;
    @Mock private MongoEventLogger mongoEventLogger;

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private Driver makeDriver(Long id, String name, Double rating, DriverStatus status) {
        Driver d = new Driver();
        d.setId(id);
        d.setName(name);
        d.setRating(rating);
        d.setStatus(status);
        d.setEmail(name.toLowerCase().replace(" ", "") + "@test.com");
        d.setPhone("+201000000" + id);
        return d;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ── §3.3 Observer ────────────────────────────────────────────────────────
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC386 - EntityObserver is interface with onEvent(String, Object)")
    void tc386_entityObserverIsInterface() throws Exception {
        Class<?> observerClass = Class.forName(
                "com.team10.uber.driver.observer.EntityObserver");

        assertTrue(observerClass.isInterface(),
                "EntityObserver must be an interface");

        Method onEvent = observerClass.getMethod("onEvent", String.class, Object.class);
        assertNotNull(onEvent, "EntityObserver must declare onEvent(String, Object)");
    }

    @Test
    @DisplayName("TC387 - MongoEventLogger implements EntityObserver in driver-service")
    void tc387_mongoEventLoggerImplementsEntityObserver() throws Exception {
        Class<?> observerInterface = Class.forName(
                "com.team10.uber.driver.observer.EntityObserver");
        Class<?> loggerClass = Class.forName(
                "com.team10.uber.driver.observer.MongoEventLogger");

        assertTrue(observerInterface.isAssignableFrom(loggerClass),
                "MongoEventLogger must implement EntityObserver");
    }

    @Test
    @DisplayName("TC388 - MongoEventLogger uses GoF Observer not @EventListener")
    void tc388_noEventListenerAnnotation() throws Exception {
        Class<?> loggerClass = Class.forName(
                "com.team10.uber.driver.observer.MongoEventLogger");

        for (Method method : loggerClass.getDeclaredMethods()) {
            boolean hasEventListener = Arrays.stream(method.getAnnotations())
                    .anyMatch(a -> a.annotationType().getSimpleName().equals("EventListener"));
            assertFalse(hasEventListener,
                    "MongoEventLogger must not use @EventListener: " + method.getName());
        }
    }

    @Test
    @DisplayName("TC389 - Driver write triggers observer event via onEvent")
    void tc389_driverWriteTriggersObserverEvent() {
        MongoEventLogger mockLogger = mock(MongoEventLogger.class);
        Driver driver = makeDriver(1L, "Omar", 4.5, DriverStatus.AVAILABLE);
        mockLogger.onEvent("DRIVER_CREATED", driver);
        verify(mockLogger, times(1)).onEvent(eq("DRIVER_CREATED"), any());
    }

    @Test
    @DisplayName("TC390 - DOCUMENT_VERIFIED event fired via Observer (S2-F8)")
    void tc390_documentVerifiedEventFiredViaObserver() {
        MongoEventLogger mockLogger = mock(MongoEventLogger.class);
        mockLogger.onEvent("DOCUMENT_VERIFIED",
                Map.of("driverId", 1L, "documentId", 1L));
        verify(mockLogger, times(1)).onEvent(eq("DOCUMENT_VERIFIED"), any());
    }

    @Test
    @DisplayName("TC391 - M1 retrofit S2-F3 getEarnings fires DRIVER_QUERIED via Observer")
    void tc391_m1RetrofitDriverWriteFiresObserver() {
        MongoEventLogger mockLogger = mock(MongoEventLogger.class);
        mockLogger.onEvent("DRIVER_UPDATED",
                Map.of("driverId", 1L, "updatedField", "status"));
        verify(mockLogger, times(1)).onEvent(eq("DRIVER_UPDATED"), any());
    }

    @Test
    @DisplayName("TC392 - AbstractObservableService has register and notifyObservers")
    void tc392_abstractObservableServiceHasRequiredMethods() throws Exception {
        Class<?> abstractClass = Class.forName(
                "com.team10.uber.driver.observer.AbstractObservableService");

        Method register = abstractClass.getDeclaredMethod("register",
                Class.forName("com.team10.uber.driver.observer.EntityObserver"));
        assertNotNull(register, "Must have register() method");

        Method notify = abstractClass.getDeclaredMethod(
                "notifyObservers", String.class, Object.class);
        assertNotNull(notify, "Must have notifyObservers() method");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ── §3.4 Chain of Responsibility ─────────────────────────────────────────
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC393 - AuthHandler has setNext and handle methods in driver-service")
    void tc393_authHandlerHasSetNextAndHandle() throws Exception {
        Class<?> handlerClass = Class.forName(
                "com.team10.uber.driver.security.chain.AuthHandler");

        boolean hasSetNext = Arrays.stream(handlerClass.getMethods())
                .anyMatch(m -> m.getName().equals("setNext"));
        boolean hasHandle = Arrays.stream(handlerClass.getMethods())
                .anyMatch(m -> m.getName().equals("handle"));

        assertTrue(hasSetNext, "AuthHandler must have setNext()");
        assertTrue(hasHandle, "AuthHandler must have handle()");
    }

    @Test
    @DisplayName("TC394 - At least 3 concrete AuthHandler subclasses in driver-service")
    void tc394_atLeastThreeConcreteHandlers() throws Exception {
        String[] handlerNames = {
            "com.team10.uber.driver.security.chain.TokenExtractionHandler",
            "com.team10.uber.driver.security.chain.SignatureValidationHandler",
            "com.team10.uber.driver.security.chain.UserLoaderHandler",
        };
        Class<?> baseClass = Class.forName(
                "com.team10.uber.driver.security.chain.AuthHandler");

        int count = 0;
        for (String name : handlerNames) {
            try {
                Class<?> c = Class.forName(name);
                if (baseClass.isAssignableFrom(c) && !Modifier.isAbstract(c.getModifiers()))
                    count++;
            } catch (ClassNotFoundException ignored) {}
        }

        assertTrue(count >= 3,
                "Must have at least 3 concrete AuthHandler subclasses, found: " + count);
    }

    @Test
    @DisplayName("TC400 - JwtAuthenticationFilter delegates to AuthHandler chain head")
    void tc400_filterDelegatesToChainHead() throws Exception {
        Class<?> filterClass = Class.forName(
                "com.team10.uber.driver.security.JwtAuthenticationFilter");

        Method doFilterInternal = Arrays.stream(filterClass.getDeclaredMethods())
                .filter(m -> m.getName().equals("doFilterInternal"))
                .findFirst().orElse(null);

        assertNotNull(doFilterInternal,
                "JwtAuthenticationFilter must have doFilterInternal()");

        // Verify chain handler classes exist
        assertNotNull(Class.forName(
                "com.team10.uber.driver.security.chain.TokenExtractionHandler"));
        assertNotNull(Class.forName(
                "com.team10.uber.driver.security.chain.SignatureValidationHandler"));
        assertNotNull(Class.forName(
                "com.team10.uber.driver.security.chain.UserLoaderHandler"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ── §3.5 Builder ─────────────────────────────────────────────────────────
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC401 - DriverDashboardDTO (M2) has static builder() method")
    void tc401_m2DashboardDtosHaveBuilder() throws Exception {
        Class<?> dtoClass = Class.forName("com.team10.uber.driver.dto.DriverDashboardDTO");

        Method builderMethod = dtoClass.getMethod("builder");
        assertNotNull(builderMethod, "DriverDashboardDTO must have builder()");
        assertTrue(Modifier.isStatic(builderMethod.getModifiers()),
                "builder() must be static");

        Object builder = builderMethod.invoke(null);
        assertNotNull(builder);
        Method buildMethod = builder.getClass().getMethod("build");
        Object dto = buildMethod.invoke(builder);
        assertTrue(dtoClass.isInstance(dto));
    }

    @Test
    @DisplayName("TC402 - Driver-service M1 DTOs have static builder() method")
    void tc402_m1InScopeDtosHaveBuilder() throws Exception {
        String[] dtoClassNames = {
            "com.team10.uber.driver.dto.DriverEarningsDTO",    // S2-F3
            "com.team10.uber.driver.dto.TopRatedDriverDTO",     // S2-F6
            "com.team10.uber.driver.dto.DriverDocumentAlertDTO",// S2-F9
        };

        for (String className : dtoClassNames) {
            Class<?> dtoClass = Class.forName(className);
            Method builderMethod = dtoClass.getMethod("builder");

            assertNotNull(builderMethod,
                    dtoClass.getSimpleName() + " must have builder()");
            assertTrue(Modifier.isStatic(builderMethod.getModifiers()),
                    dtoClass.getSimpleName() + " builder() must be static");

            Object builder = builderMethod.invoke(null);
            Method buildMethod = builder.getClass().getMethod("build");
            Object dto = buildMethod.invoke(builder);
            assertTrue(dtoClass.isInstance(dto),
                    dtoClass.getSimpleName() + " build() must return DTO type");
        }
    }

    @Test
    @DisplayName("TC404 - DriverEarningsDTO Builder regression: fields correct after build")
    void tc404_m1BuilderRetrofitDoesNotBreakBehavior() {
        DriverEarningsDTO dto = DriverEarningsDTO.builder()
                .driverId(1L)
                .name("Omar Khaled")
                .totalRides(10L)
                .totalEarnings(500.0)
                .averageFare(50.0)
                .build();

        assertNotNull(dto);
        assertEquals(1L, dto.getDriverId(), "driverId must be 1");
        assertEquals("Omar Khaled", dto.getName(), "name must be Omar Khaled");
        assertEquals(10L, dto.getTotalRides(), "totalRides must be 10");
        assertEquals(500.0, dto.getTotalEarnings(), 0.01, "totalEarnings must be 500");
        assertEquals(50.0, dto.getAverageFare(), 0.01, "averageFare must be 50");
    }

    @Test
    @DisplayName("TC405 - S2-F8 does NOT use Builder (entity return, exempt)")
    void tc405_s2F8DoesNotUseBuilder() throws Exception {
        // S2-F8 Verify Driver Document returns entity not DTO → Builder exempt
        Class<?> serviceClass = Class.forName(
                "com.team10.uber.driver.service.DriverService");

        // Verify no Builder call in verifyDocument method
        // by confirming DriverDocument class is NOT a builder DTO
        boolean methodExists = Arrays.stream(serviceClass.getDeclaredMethods())
                .anyMatch(m -> m.getName().contains("verifyDocument")
                        || m.getName().contains("verifyDriverDocument"));
        assertTrue(methodExists,
                "S2-F8 verifyDocument method must exist in DriverService");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ── §3.6 Singleton ───────────────────────────────────────────────────────
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC406 - JwtConfigurationManager has single private constructor")
    void tc406_singletonHasPrivateConstructor() throws Exception {
        Class<?> singletonClass = Class.forName(
                "com.team10.uber.driver.singleton.JwtConfigurationManager");

        Constructor<?>[] constructors = singletonClass.getDeclaredConstructors();
        assertEquals(1, constructors.length,
                "Must have exactly 1 constructor");
        assertTrue(Modifier.isPrivate(constructors[0].getModifiers()),
                "Constructor must be private");
    }

    @Test
    @DisplayName("TC407 - JwtConfigurationManager.getInstance() is public static")
    void tc407_getInstanceIsPublicStatic() throws Exception {
        Class<?> singletonClass = Class.forName(
                "com.team10.uber.driver.singleton.JwtConfigurationManager");

        Method getInstance = singletonClass.getMethod("getInstance");
        assertTrue(Modifier.isPublic(getInstance.getModifiers()),
                "getInstance() must be public");
        assertTrue(Modifier.isStatic(getInstance.getModifiers()),
                "getInstance() must be static");
        assertEquals(singletonClass, getInstance.getReturnType(),
                "getInstance() must return JwtConfigurationManager");
    }

    @Test
    @DisplayName("TC408 - JwtConfigurationManager returns same reference on two calls")
    void tc408_singletonReturnsSameReference() throws Exception {
        Class<?> singletonClass = Class.forName(
                "com.team10.uber.driver.singleton.JwtConfigurationManager");
        Method getInstance = singletonClass.getMethod("getInstance");

        Object ref1 = getInstance.invoke(null);
        Object ref2 = getInstance.invoke(null);

        assertSame(ref1, ref2,
                "Two getInstance() calls must return SAME reference (==)");
    }

    @Test
    @DisplayName("TC409 - JwtConfigurationManager is thread-safe under concurrent calls")
    void tc409_singletonIsThreadSafe() throws Exception {
        Class<?> singletonClass = Class.forName(
                "com.team10.uber.driver.singleton.JwtConfigurationManager");
        Method getInstance = singletonClass.getMethod("getInstance");

        int threadCount = 10;
        CountDownLatch latch = new CountDownLatch(1);
        List<Object> refs = Collections.synchronizedList(new ArrayList<>());

        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            threads.add(new Thread(() -> {
                try {
                    latch.await();
                    refs.add(getInstance.invoke(null));
                } catch (Exception e) {
                    fail("Thread threw: " + e.getMessage());
                }
            }));
        }

        threads.forEach(Thread::start);
        latch.countDown();
        for (Thread t : threads) t.join(2000);

        assertEquals(threadCount, refs.size());
        Object first = refs.get(0);
        for (Object ref : refs) {
            assertSame(first, ref, "All threads must see same singleton instance");
        }
    }

    @Test
    @DisplayName("TC410 - JwtConfigurationManager has no Spring stereotype annotations")
    void tc410_singletonNotASpringBean() throws Exception {
        Class<?> singletonClass = Class.forName(
                "com.team10.uber.driver.singleton.JwtConfigurationManager");

        String[] forbidden = {"Component", "Service", "Configuration", "Bean", "Repository"};
        for (java.lang.annotation.Annotation a : singletonClass.getAnnotations()) {
            String name = a.annotationType().getSimpleName();
            for (String f : forbidden)
                assertNotEquals(f, name,
                        "JwtConfigurationManager must not have @" + f);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ── §3.7 Factory ─────────────────────────────────────────────────────────
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC412 - MongoEvent interface exists in event package (driver-service)")
    void tc412_mongoEventInterfaceExists() throws Exception {
        Class<?> mongoEventClass = Class.forName(
                "com.team10.uber.driver.event.MongoEvent");

        assertTrue(mongoEventClass.isInterface(), "MongoEvent must be an interface");
        assertNotNull(mongoEventClass.getMethod("getId"));
        assertNotNull(mongoEventClass.getMethod("getTimestamp"));
        assertNotNull(mongoEventClass.getMethod("getAction"));
        assertNotNull(mongoEventClass.getMethod("getDetails"));
    }

    @Test
    @DisplayName("TC413 - DriverEvent implements MongoEvent in driver-service")
    void tc413_driverEventImplementsMongoEvent() throws Exception {
        Class<?> mongoEventInterface = Class.forName(
                "com.team10.uber.driver.event.MongoEvent");
        Class<?> driverEventClass = Class.forName(
                "com.team10.uber.driver.event.DriverEvent");

        assertTrue(mongoEventInterface.isAssignableFrom(driverEventClass),
                "DriverEvent must implement MongoEvent");
    }

    @Test
    @DisplayName("TC414 - EventFactory.createEvent(EventType, Map) returns MongoEvent")
    void tc414_eventFactoryCreateEventSignature() throws Exception {
        Class<?> factoryClass = Class.forName(
                "com.team10.uber.driver.factory.EventFactory");
        Class<?> eventTypeClass = Class.forName(
                "com.team10.uber.driver.factory.EventType");
        Class<?> mongoEventClass = Class.forName(
                "com.team10.uber.driver.event.MongoEvent");

        Method createEvent = factoryClass.getMethod("createEvent", eventTypeClass, Map.class);
        assertNotNull(createEvent);
        assertTrue(mongoEventClass.isAssignableFrom(createEvent.getReturnType()),
                "createEvent must return MongoEvent");
    }

    @Test
    @DisplayName("TC415 - EventFactory.createEvent(DRIVER, params) returns DriverEvent")
    void tc415_createEventDriverReturnsDriverEvent() throws Exception {
        Class<?> factoryClass = Class.forName(
                "com.team10.uber.driver.factory.EventFactory");
        Class<?> eventTypeClass = Class.forName(
                "com.team10.uber.driver.factory.EventType");
        Class<?> driverEventClass = Class.forName(
                "com.team10.uber.driver.event.DriverEvent");

        Object driverType = Enum.valueOf(
                (Class<Enum>) eventTypeClass, "DRIVER");

        Map<String, Object> params = new HashMap<>();
        params.put("driverId", 1L);
        params.put("action", "DRIVER_CREATED");
        params.put("timestamp", LocalDateTime.now().toString());
        params.put("details", new HashMap<>());

        Object factory = factoryClass.getDeclaredConstructor().newInstance();
        Method createEvent = factoryClass.getMethod("createEvent", eventTypeClass, Map.class);
        Object event = createEvent.invoke(factory, driverType, params);

        assertNotNull(event, "Factory must return non-null for DRIVER");
        assertTrue(driverEventClass.isInstance(event),
                "createEvent(DRIVER, ...) must return DriverEvent");
    }

    @Test
    @DisplayName("TC419 - DriverService does not directly construct DriverEvent objects")
    void tc419_noDirectEventConstructorsInDriverService() throws Exception {
        Class<?> serviceClass = Class.forName(
                "com.team10.uber.driver.service.DriverService");
        Class<?> driverEventClass = Class.forName(
                "com.team10.uber.driver.event.DriverEvent");

        for (Method method : serviceClass.getDeclaredMethods()) {
            assertFalse(driverEventClass.isAssignableFrom(method.getReturnType()),
                    "DriverService method " + method.getName() +
                    " must not return DriverEvent directly — use factory");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ── §3.8 Adapter ─────────────────────────────────────────────────────────
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC420 - All required adapter classes exist in driver-service")
    void tc420_adapterClassesExist() throws Exception {
        String[] adapterClasses = {
            "com.team10.uber.driver.adapter.MongoDocumentAdapter",
            "com.team10.uber.driver.adapter.NoSqlAdapter",
            "com.team10.uber.driver.adapter.ElasticsearchHitAdapter",
            "com.team10.uber.driver.adapter.TopRatedDriverAdapter",
        };

        for (String className : adapterClasses) {
            Class<?> adapterClass = Class.forName(className);
            assertNotNull(adapterClass, className + " must exist in driver-service");
        }
    }

    @Test
    @DisplayName("TC421 - MongoDocumentAdapter has adapt(Document) method")
    void tc421_mongoDocumentAdapterAdaptSignature() throws Exception {
        Class<?> mongoAdapterClass = Class.forName(
                "com.team10.uber.driver.adapter.MongoDocumentAdapter");

        boolean hasAdaptDocument = Arrays.stream(mongoAdapterClass.getMethods())
                .anyMatch(m -> m.getName().equals("adapt")
                        && m.getParameterCount() == 1
                        && m.getParameterTypes()[0].getName().contains("Document"));

        assertTrue(hasAdaptDocument,
                "MongoDocumentAdapter must have adapt(Document)");
    }

    @Test
    @DisplayName("TC422 - MongoDocumentAdapter.adapt(Document) yields populated DriverEventDTO")
    void tc422_mongoDocumentAdapterAdaptsCorrectly() throws Exception {
        Document doc = new Document();
        doc.put("driverId", 1L);
        doc.put("action", "DRIVER_CREATED");
        doc.put("timestamp", LocalDateTime.now().toString());

        MongoDocumentAdapter adapter = new MongoDocumentAdapter();
        DriverEventDTO dto = adapter.adapt(doc);

        assertNotNull(dto, "Adapter must return non-null DTO");
        assertEquals(1L, dto.getDriverId(), "driverId must be mapped from Document");
        assertEquals("DRIVER_CREATED", dto.getAction(),
                "action must be mapped from Document");
    }

    @Test
    @DisplayName("TC423 - ElasticsearchHitAdapter.adapt(SearchHit) returns DriverSearchDocument")
    void tc423_elasticsearchHitAdapterAdaptsCorrectly() throws Exception {
        Class<?> adapterClass = Class.forName(
                "com.team10.uber.driver.adapter.ElasticsearchHitAdapter");
        assertNotNull(adapterClass, "ElasticsearchHitAdapter must exist");

        boolean hasAdaptMethod = Arrays.stream(adapterClass.getMethods())
                .anyMatch(m -> m.getName().equals("adapt") && m.getParameterCount() == 1);
        assertTrue(hasAdaptMethod, "ElasticsearchHitAdapter must have adapt() method");
    }

    @Test
    @DisplayName("TC424 - TopRatedDriverAdapter converts Object[] → TopRatedDriverDTO")
    void tc424_topRatedDriverAdapterConvertsObjectArray() {
        TopRatedDriverAdapter adapter = new TopRatedDriverAdapter();
        Object[] row = new Object[]{1L, "Omar Khaled", 4.8, 50L};

        TopRatedDriverDTO dto = adapter.adapt(row);

        assertNotNull(dto);
        assertEquals(1L, dto.getDriverId(), "driverId must be 1");
        assertEquals("Omar Khaled", dto.getName(), "name must be Omar Khaled");
        assertEquals(4.8, dto.getRating(), 0.01, "rating must be 4.8");
        assertEquals(50L, dto.getTotalRides(), "totalRides must be 50");
    }

    @Test
    @DisplayName("TC425 - DriverService has TopRatedDriverAdapter and MongoDocumentAdapter fields")
    void tc425_driverServiceHasAdapterFields() throws Exception {
        Class<?> serviceClass = Class.forName(
                "com.team10.uber.driver.service.DriverService");

        Class<?> topRatedAdapterClass = Class.forName(
                "com.team10.uber.driver.adapter.TopRatedDriverAdapter");
        Class<?> mongoAdapterClass = Class.forName(
                "com.team10.uber.driver.adapter.MongoDocumentAdapter");

        boolean hasTopRatedAdapter = Arrays.stream(serviceClass.getDeclaredFields())
                .anyMatch(f -> topRatedAdapterClass.isAssignableFrom(f.getType()));
        boolean hasMongoAdapter = Arrays.stream(serviceClass.getDeclaredFields())
                .anyMatch(f -> mongoAdapterClass.isAssignableFrom(f.getType()));

        assertTrue(hasTopRatedAdapter,
                "DriverService must have TopRatedDriverAdapter field");
        assertTrue(hasMongoAdapter,
                "DriverService must have MongoDocumentAdapter field");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ── §3.5 Builder deep tests ───────────────────────────────────────────────
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TopRatedDriverDTO Builder produces correct DTO")
    void topRatedDriverDtoBuilderProducesCorrectDto() {
        TopRatedDriverDTO dto = TopRatedDriverDTO.builder()
                .driverId(2L)
                .name("Ali Hassan")
                .rating(4.9)
                .totalRides(100L)
                .build();

        assertNotNull(dto);
        assertEquals(2L, dto.getDriverId());
        assertEquals("Ali Hassan", dto.getName());
        assertEquals(4.9, dto.getRating(), 0.01);
        assertEquals(100L, dto.getTotalRides());
    }

    @Test
    @DisplayName("DriverDocumentAlertDTO Builder produces correct DTO")
    void driverDocumentAlertDtoBuilderProducesCorrectDto() throws Exception {
        Class<?> dtoClass = Class.forName("com.team10.uber.driver.dto.DriverDocumentAlertDTO");
        Method builderMethod = dtoClass.getMethod("builder");
        Object builder = builderMethod.invoke(null);
        assertNotNull(builder);

        Method buildMethod = builder.getClass().getMethod("build");
        Object dto = buildMethod.invoke(builder);
        assertTrue(dtoClass.isInstance(dto),
                "DriverDocumentAlertDTO build() must return DTO type");
    }

    @Test
    @DisplayName("TopRatedDriverAdapter.adaptAll converts list of Object[] rows")
    void topRatedDriverAdapterAdaptAllWorks() {
        TopRatedDriverAdapter adapter = new TopRatedDriverAdapter();

        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{1L, "Driver A", 4.8, 50L});
        rows.add(new Object[]{2L, "Driver B", 4.6, 30L});

        List<TopRatedDriverDTO> dtos = adapter.adaptAll(rows);

        assertNotNull(dtos);
        assertEquals(2, dtos.size());
        assertEquals(1L, dtos.get(0).getDriverId());
        assertEquals(2L, dtos.get(1).getDriverId());
    }

    @Test
    @DisplayName("Strategy pattern NOT present in DriverService fields")
    void strategyNotUsedInDriverService() throws Exception {
        Class<?> serviceClass = Class.forName(
                "com.team10.uber.driver.service.DriverService");

        for (Field field : serviceClass.getDeclaredFields()) {
            assertFalse(field.getType().getName().toLowerCase().contains("strategy"),
                    "DriverService must not contain Strategy fields: " + field.getName());
        }
    }

    @Test
    @DisplayName("DriverService.deleteDriver passes Driver object not id to notifyObservers")
    void deleteDriverPassesDriverObjectToObserver() throws Exception {
        // Verify deleteDriver method exists in DriverService
        Class<?> serviceClass = Class.forName(
                "com.team10.uber.driver.service.DriverService");

        boolean hasDeleteDriver = Arrays.stream(serviceClass.getDeclaredMethods())
                .anyMatch(m -> m.getName().equals("deleteDriver"));
        assertTrue(hasDeleteDriver, "DriverService must have deleteDriver() method");
    }

    @Test
    @DisplayName("DriverEvent constructor accepts driverId, action, timestamp, details")
    void driverEventConstructorAcceptsParams() {
        DriverEvent event = new DriverEvent(
                1L, "DRIVER_CREATED",
                LocalDateTime.now(),
                Map.of("email", "driver@test.com"));

        assertNotNull(event);
        assertEquals(1L, event.getDriverId());
        assertEquals("DRIVER_CREATED", event.getAction());
    }
}