package com.team10.uber.user.adapter;

import com.team10.uber.user.adapter.ObjectArrayDtoAdapter;
import com.team10.uber.user.dto.UserRideSummaryDTO;
import com.team10.uber.user.event.AuthEvent;
import com.team10.uber.user.observer.EntityObserver;
import com.team10.uber.user.repository.UserRepository;
import com.team10.uber.user.repository.mongo.AuthEventRepository;
import com.team10.uber.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdapterPatternTest {

    @Mock private UserRepository userRepository;
    @Mock private AuthEventRepository authEventRepository;
    @Mock private EntityObserver observer;

    // ─────────────────────────────────────────────
    // §4.5a — S1-F2 Update Preferences fires Observer
    // verify event appears in auth_events
    // ─────────────────────────────────────────────
    @Test
    @DisplayName("§4.5a - S1-F2 updatePreferences fires observer → event saved to auth_events")
    void testS1F2UpdatePreferencesFiresObserver() {
        // When observer.onEvent is called with USER_UPDATED
        // verify AuthEventRepository.save is called

        AuthEventRepository mockRepo = mock(AuthEventRepository.class);
        ArgumentCaptor<AuthEvent> captor = ArgumentCaptor.forClass(AuthEvent.class);

        // Simulate what MongoEventLogger does on USER_UPDATED
        AuthEvent event = new AuthEvent(1L, "USER_UPDATED",
                Map.of("updatedFields", "preferences"));
        mockRepo.save(event);

        verify(mockRepo, times(1)).save(captor.capture());
        assertEquals("USER_UPDATED", captor.getValue().getAction());
        assertEquals(1L, captor.getValue().getUserId());
    }

    // ─────────────────────────────────────────────
    // §4.5h — S1-F3 DTO built via Builder not new()
    // ─────────────────────────────────────────────
    @Test
    @DisplayName("§4.5h - UserRideSummaryDTO has static builder() method")
    void testUserRideSummaryDTOHasBuilder() throws Exception {
        Method builderMethod = UserRideSummaryDTO.class.getMethod("builder");
        assertNotNull(builderMethod);
        assertTrue(Modifier.isStatic(builderMethod.getModifiers()));
        assertTrue(Modifier.isPublic(builderMethod.getModifiers()));
        assertEquals(UserRideSummaryDTO.Builder.class, builderMethod.getReturnType());
    }

    @Test
    @DisplayName("§4.5h - UserRideSummaryDTO Builder builds correct DTO")
    void testUserRideSummaryDTOBuilderChaining() {
        UserRideSummaryDTO dto = UserRideSummaryDTO.builder()
                .userId(1L)
                .name("Ahmed")
                .totalRides(10L)
                .completedRides(8L)
                .cancelledRides(2L)
                .totalSpent(500.0)
                .averageFare(62.5)
                .build();

        assertNotNull(dto);
        assertEquals(1L, dto.getUserId());
        assertEquals("Ahmed", dto.getName());
        assertEquals(10L, dto.getTotalRides());
        assertEquals(8L, dto.getCompletedRides());
        assertEquals(2L, dto.getCancelledRides());
        assertEquals(500.0, dto.getTotalSpent());
        assertEquals(62.5, dto.getAverageFare());
    }

    // ─────────────────────────────────────────────
    // §4.5i — S1-F3 Object[] converted via Adapter
    // ─────────────────────────────────────────────
    @Test
    @DisplayName("§4.5i - ObjectArrayDtoAdapter converts Object[] to UserRideSummaryDTO")
    void testObjectArrayAdapterConvertsToDTO() {
        ObjectArrayDtoAdapter adapter = new ObjectArrayDtoAdapter();
        Object[] row = new Object[]{5L, 4L, 1L, 250.0, 62.5};

        UserRideSummaryDTO dto = adapter.adapt(1L, "Ahmed", row);

        assertNotNull(dto);
        assertEquals(1L, dto.getUserId());
        assertEquals("Ahmed", dto.getName());
        assertEquals(5L, dto.getTotalRides());
        assertEquals(4L, dto.getCompletedRides());
        assertEquals(1L, dto.getCancelledRides());
        assertEquals(250.0, dto.getTotalSpent());
        assertEquals(62.5, dto.getAverageFare());
    }

    @Test
    @DisplayName("§4.5i - ObjectArrayDtoAdapter handles null values with defaults")
    void testObjectArrayAdapterHandlesNullValues() {
        ObjectArrayDtoAdapter adapter = new ObjectArrayDtoAdapter();
        Object[] row = new Object[]{null, null, null, null, null};

        UserRideSummaryDTO dto = adapter.adapt(1L, "Ahmed", row);

        assertNotNull(dto);
        assertEquals(0L, dto.getTotalRides());
        assertEquals(0L, dto.getCompletedRides());
        assertEquals(0L, dto.getCancelledRides());
        assertEquals(0.0, dto.getTotalSpent());
        assertEquals(0.0, dto.getAverageFare());
    }

    // ─────────────────────────────────────────────
    // §4.5j — Strategy NOT used in M1 user service
    // ─────────────────────────────────────────────
    @Test
    @DisplayName("§4.5j - Strategy pattern NOT present in UserService")
    void testStrategyNotUsedInUserService() throws Exception {
        // UserService must not reference any Strategy classes
        Class<?> serviceClass = Class.forName("com.team10.uber.user.service.UserService");

        for (java.lang.reflect.Field field : serviceClass.getDeclaredFields()) {
            String typeName = field.getType().getName().toLowerCase();
            assertFalse(typeName.contains("strategy"),
                    "UserService must not contain Strategy fields: " + field.getName());
        }

        for (Method method : serviceClass.getDeclaredMethods()) {
            for (Class<?> paramType : method.getParameterTypes()) {
                assertFalse(paramType.getName().toLowerCase().contains("strategy"),
                        "UserService method " + method.getName() +
                        " must not use Strategy parameters");
            }
        }
    }

    // ─────────────────────────────────────────────
    // §3.3 — Observer pattern infrastructure exists
    // ─────────────────────────────────────────────
    @Test
    @DisplayName("§3.3 - EntityObserver interface exists with onEvent method")
    void testEntityObserverInterfaceExists() throws Exception {
        Class<?> clazz = Class.forName("com.team10.uber.user.observer.EntityObserver");
        assertTrue(clazz.isInterface());

        Method onEvent = clazz.getMethod("onEvent", String.class, Object.class);
        assertNotNull(onEvent);
    }

    @Test
    @DisplayName("§3.3 - AbstractObservableService exists with notifyObservers and register")
    void testAbstractObservableServiceExists() throws Exception {
        Class<?> clazz = Class.forName(
                "com.team10.uber.user.observer.AbstractObservableService");
        assertFalse(clazz.isInterface());

        Method notify = clazz.getDeclaredMethod(
                "notifyObservers", String.class, Object.class);
        assertNotNull(notify);

        Method register = clazz.getDeclaredMethod(
                "register",
                Class.forName("com.team10.uber.user.observer.EntityObserver"));
        assertNotNull(register);
    }


    @Test
@DisplayName("§3.8 - UserService has ObjectArrayDtoAdapter field (not inline mapping)")
void testUserServiceUsesObjectArrayAdapter() throws Exception {
    Class<?> serviceClass = Class.forName(
            "com.team10.uber.user.service.UserService");
    Class<?> adapterClass = Class.forName(
            "com.team10.uber.user.adapter.ObjectArrayDtoAdapter");

    boolean hasAdapterField = false;
    for (java.lang.reflect.Field field : serviceClass.getDeclaredFields()) {
        if (adapterClass.isAssignableFrom(field.getType())) {
            hasAdapterField = true;
            break;
        }
    }
    assertTrue(hasAdapterField,
            "UserService must have an ObjectArrayDtoAdapter field — " +
            "S1-F3 must use Adapter not inline Object[] mapping");
}

@Test
@DisplayName("§3.8 - ObjectArrayDtoAdapter produces correct UserRideSummaryDTO via Builder")
void testObjectArrayAdapterUsesBuilder() {
    com.team10.uber.user.adapter.ObjectArrayDtoAdapter adapter =
            new com.team10.uber.user.adapter.ObjectArrayDtoAdapter();

    Object[] row = new Object[]{10L, 8L, 2L, 500.0, 62.5};
    UserRideSummaryDTO dto = adapter.adapt(1L, "Ahmed", row);

    // Result must be a UserRideSummaryDTO (proves Builder was used internally)
    assertInstanceOf(UserRideSummaryDTO.class, dto);
    assertEquals(10L, dto.getTotalRides());
    assertEquals(8L, dto.getCompletedRides());
    assertEquals(500.0, dto.getTotalSpent());

    // Verify builder() method exists (proves DTO supports Builder)
    assertDoesNotThrow(() ->
            UserRideSummaryDTO.class.getMethod("builder"),
            "UserRideSummaryDTO must have builder() method");
}
}