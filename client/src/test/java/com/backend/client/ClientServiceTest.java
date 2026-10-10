package com.backend.client;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.backend.client.dto.*;
import com.backend.client.model.*;
import com.backend.client.repository.*;
import com.backend.client.service.ClientService;
import jakarta.validation.Validation;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import tools.jackson.databind.json.JsonMapper;

class ClientServiceTest {
  ClientRepository repo;
  OutboxRepository outbox;
  ClientService service;

  @BeforeEach
  void setup() {
    repo = mock(ClientRepository.class);
    outbox = mock(OutboxRepository.class);
    service =
        new ClientService(
            repo,
            outbox,
            JsonMapper.builder().build(),
            Validation.buildDefaultValidatorFactory().getValidator());
  }

  @Test
  void hashesPasswordAndPublishesSafeSnapshot() {
    when(repo.saveAndFlush(any()))
        .thenAnswer(
            i -> {
              Client c = i.getArgument(0);
              c.id = 1L;
              return c;
            });
    var result =
        service.create(
            new ClientRequest(
                "Ana",
                "Pérez",
                "Ana Pérez",
                101L,
                "123",
                "F",
                30,
                "Quito",
                "0991234567",
                "password123",
                true));
    var client = org.mockito.ArgumentCaptor.forClass(Client.class);
    verify(repo).saveAndFlush(client.capture());
    assertThat(new BCryptPasswordEncoder().matches("password123", client.getValue().password))
        .isTrue();
    var event = org.mockito.ArgumentCaptor.forClass(OutboxEvent.class);
    verify(outbox).save(event.capture());
    assertThat(event.getValue().payload).contains("Ana").doesNotContain("password", "$2");
    assertThat(JsonMapper.builder().build().writeValueAsString(result)).doesNotContain("password");
  }

  @Test
  void patchPreservesPassword() {
    Client c = new Client();
    c.id = 1L;
    c.firstName = "Ana";
    c.lastName = "Pérez";
    c.completeName = "Ana Pérez";
    c.dniTypeId = 101L;
    c.dni = "123";
    c.gender = "F";
    c.age = 30;
    c.address = "Quito";
    c.phone = "099";
    c.password = "existing-hash";
    when(repo.findById(1L)).thenReturn(Optional.of(c));
    service.patch(1L, Map.of("completeName", "Eva Pérez"));
    assertThat(c.completeName).isEqualTo("Eva Pérez");
    assertThat(c.password).isEqualTo("existing-hash");
  }

  @Test
  void rejectsInvalidOrUnknownFields() {
    assertThatThrownBy(
            () ->
                service.create(
                    new ClientRequest(
                        "", "Pérez", "Ana Pérez", 101L, "123", "F", -1, "x", "x", "short", true)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.patch(1L, Map.of("id", 7)))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(outbox);
  }

  @Test
  void inactiveStateCreatesDeletionTimestampAndReactivationClearsIt() {
    Client c = new Client();
    c.id = 1L;
    when(repo.findById(1L)).thenReturn(Optional.of(c));
    service.update(
        1L,
        new ClientRequest(
            "Ana", "Pérez", "Ana Pérez", 101L, "123", "F", 30, "Quito", "099", "password123", false));
    var deletedAt = c.deletedAt;
    assertThat(deletedAt).isNotNull();
    assertThat(c.isActive).isFalse();
    service.delete(1L);
    assertThat(c.deletedAt).isEqualTo(deletedAt);
    service.update(
        1L,
        new ClientRequest(
            "Ana", "Pérez", "Ana Pérez", 101L, "123", "F", 30, "Quito", "099", "password123", true));
    assertThat(c.deletedAt).isNull();
    assertThat(c.isActive).isTrue();
  }
}
