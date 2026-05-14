package com.team10.uber.driver.service;

import com.team10.uber.contracts.dto.UserDTO;
import com.team10.uber.contracts.feign.UserServiceClient;
import com.team10.uber.driver.cache.RedisCacheService;
import com.team10.uber.driver.messaging.DriverEventPublisher;
import com.team10.uber.driver.model.Driver;
import com.team10.uber.driver.model.DriverDocument;
import com.team10.uber.driver.observer.MongoEventLogger;
import com.team10.uber.driver.repository.DriverDocumentRepository;
import com.team10.uber.driver.repository.DriverRepository;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DriverDocumentServiceS2F8Test {

    @Mock DriverRepository driverRepository;
    @Mock DriverDocumentRepository driverDocumentRepository;
    @Mock MongoEventLogger mongoEventLogger;
    @Mock RedisCacheService cache;
    @Mock DriverEventPublisher driverEventPublisher;
    @Mock UserServiceClient userServiceClient;
    @InjectMocks DriverDocumentService driverDocumentService;

    private Driver driver1;
    private DriverDocument validDoc;
    private DriverDocument expiredDoc;
    private DriverDocument alreadyVerifiedDoc;
    private DriverDocument wrongOwnerDoc;
    // UserDTO(id, name, email, phone, role, status) — nulls for unused fields
    private UserDTO adminUser;
    private UserDTO riderUser;

    @BeforeEach
    void setUp() {
        driver1 = new Driver();
        driver1.setId(1L);

        validDoc = new DriverDocument();
        validDoc.setId(10L);
        validDoc.setDriver(driver1);
        validDoc.setExpiryDate(LocalDate.of(2027, 12, 31));
        validDoc.setVerified(false);

        expiredDoc = new DriverDocument();
        expiredDoc.setId(11L);
        expiredDoc.setDriver(driver1);
        expiredDoc.setExpiryDate(LocalDate.of(2020, 1, 1));
        expiredDoc.setVerified(false);

        alreadyVerifiedDoc = new DriverDocument();
        alreadyVerifiedDoc.setId(12L);
        alreadyVerifiedDoc.setDriver(driver1);
        alreadyVerifiedDoc.setExpiryDate(LocalDate.of(2027, 12, 31));
        alreadyVerifiedDoc.setVerified(true);

        Driver driver2 = new Driver();
        driver2.setId(2L);
        wrongOwnerDoc = new DriverDocument();
        wrongOwnerDoc.setId(20L);
        wrongOwnerDoc.setDriver(driver2);
        wrongOwnerDoc.setExpiryDate(LocalDate.of(2027, 12, 31));
        wrongOwnerDoc.setVerified(false);

        adminUser = new UserDTO(3L, "Admin User", null, null, "ADMIN", null);
        riderUser = new UserDTO(2L, "Rider User", null, null, "RIDER", null);
    }

    @Test
    @DisplayName("Scenario 1 — happy path: 200, verified=true, JSONB updated, event published")
    void happyPath_verifiesDocument() {
        when(driverRepository.findById(1L)).thenReturn(Optional.of(driver1));
        when(driverDocumentRepository.findById(10L)).thenReturn(Optional.of(validDoc));
        when(userServiceClient.getUser(3L)).thenReturn(adminUser);
        when(driverDocumentRepository.save(any())).thenReturn(validDoc);

        driverDocumentService.verifyDriverDocument(1L, 10L, 3L);

        assertThat(validDoc.getVerified()).isTrue();
        assertThat(validDoc.getMetadata()).containsKey("verifiedAt");
        assertThat(validDoc.getMetadata()).containsEntry("verifiedBy", 3L);
        verify(driverDocumentRepository).save(validDoc);
        verify(driverEventPublisher).publishDocumentVerified(1L, 10L, 3L);
    }

    @Test
    @DisplayName("Scenario 2 — Feign 404 for caller → 403 (not 404)")
    void callerNotFound_throws403NotFourOhFour() {
        when(driverRepository.findById(1L)).thenReturn(Optional.of(driver1));
        when(driverDocumentRepository.findById(10L)).thenReturn(Optional.of(validDoc));
        when(userServiceClient.getUser(99L)).thenThrow(FeignException.NotFound.class);

        assertThatThrownBy(() -> driverDocumentService.verifyDriverDocument(1L, 10L, 99L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(403);
                });

        verify(driverDocumentRepository, never()).save(any());
        verify(driverEventPublisher, never()).publishDocumentVerified(any(), any(), any());
    }

    @Test
    @DisplayName("Scenario 3 — caller role=RIDER → 403")
    void callerNotAdmin_throws403() {
        when(driverRepository.findById(1L)).thenReturn(Optional.of(driver1));
        when(driverDocumentRepository.findById(10L)).thenReturn(Optional.of(validDoc));
        when(userServiceClient.getUser(2L)).thenReturn(riderUser);

        assertThatThrownBy(() -> driverDocumentService.verifyDriverDocument(1L, 10L, 2L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(403);
                });

        verify(driverDocumentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Scenario 4 — expired document → 400 (Feign NOT called — local check fails first)")
    void expiredDocument_throws400_feignNotCalled() {
        when(driverRepository.findById(1L)).thenReturn(Optional.of(driver1));
        when(driverDocumentRepository.findById(11L)).thenReturn(Optional.of(expiredDoc));

        assertThatThrownBy(() -> driverDocumentService.verifyDriverDocument(1L, 11L, 3L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");

        // Feign NOT called — expiry is a local check that fails before the network call
        verify(userServiceClient, never()).getUser(any());
        verify(driverDocumentRepository, never()).save(any());
        verify(driverEventPublisher, never()).publishDocumentVerified(any(), any(), any());
    }

    @Test
    @DisplayName("Scenario 5 — document belongs to different driver → 400")
    void documentOwnershipMismatch_throws400() {
        when(driverRepository.findById(1L)).thenReturn(Optional.of(driver1));
        when(driverDocumentRepository.findById(20L)).thenReturn(Optional.of(wrongOwnerDoc));

        assertThatThrownBy(() -> driverDocumentService.verifyDriverDocument(1L, 20L, 3L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");

        verify(userServiceClient, never()).getUser(any());
    }

    @Test
    @DisplayName("Scenario 6 — document not found → 404")
    void documentNotFound_throws404() {
        when(driverRepository.findById(1L)).thenReturn(Optional.of(driver1));
        when(driverDocumentRepository.findById(9999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> driverDocumentService.verifyDriverDocument(1L, 9999L, 3L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");

        verify(userServiceClient, never()).getUser(any());
    }

    @Test
    @DisplayName("Scenario 7 — driver not found → 404")
    void driverNotFound_throws404() {
        when(driverRepository.findById(9999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> driverDocumentService.verifyDriverDocument(9999L, 10L, 3L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");

        verify(userServiceClient, never()).getUser(any());
    }

    @Test
    @DisplayName("Scenario 8 — already verified → 400")
    void alreadyVerified_throws400() {
        when(driverRepository.findById(1L)).thenReturn(Optional.of(driver1));
        when(driverDocumentRepository.findById(12L)).thenReturn(Optional.of(alreadyVerifiedDoc));

        assertThatThrownBy(() -> driverDocumentService.verifyDriverDocument(1L, 12L, 3L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");

        verify(userServiceClient, never()).getUser(any());
    }

    @Test
    @DisplayName("Scenario 9 — user-service 5xx → 503")
    void userServiceUnavailable_throws503() {
        when(driverRepository.findById(1L)).thenReturn(Optional.of(driver1));
        when(driverDocumentRepository.findById(10L)).thenReturn(Optional.of(validDoc));
        when(userServiceClient.getUser(3L)).thenThrow(FeignException.InternalServerError.class);

        assertThatThrownBy(() -> driverDocumentService.verifyDriverDocument(1L, 10L, 3L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("503");

        verify(driverDocumentRepository, never()).save(any());
        verify(driverEventPublisher, never()).publishDocumentVerified(any(), any(), any());
    }

    @Test
    @DisplayName("publishDocumentVerified called AFTER save (publish-after-commit §2.11)")
    void publishCalledAfterSave() {
        when(driverRepository.findById(1L)).thenReturn(Optional.of(driver1));
        when(driverDocumentRepository.findById(10L)).thenReturn(Optional.of(validDoc));
        when(userServiceClient.getUser(3L)).thenReturn(adminUser);
        when(driverDocumentRepository.save(any())).thenReturn(validDoc);

        var inOrder = inOrder(driverDocumentRepository, driverEventPublisher);

        driverDocumentService.verifyDriverDocument(1L, 10L, 3L);

        inOrder.verify(driverDocumentRepository).save(any());
        inOrder.verify(driverEventPublisher).publishDocumentVerified(any(), any(), any());
    }

    @Test
    @DisplayName("JSONB metadata contains verifiedBy=callerId from header, not from body")
    void metadata_verifiedByIsCallerId() {
        when(driverRepository.findById(1L)).thenReturn(Optional.of(driver1));
        when(driverDocumentRepository.findById(10L)).thenReturn(Optional.of(validDoc));
        when(userServiceClient.getUser(3L)).thenReturn(adminUser);
        when(driverDocumentRepository.save(any())).thenReturn(validDoc);

        driverDocumentService.verifyDriverDocument(1L, 10L, 3L);

        assertThat(validDoc.getMetadata()).containsEntry("verifiedBy", 3L);
    }
}
