package com.backend.account;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.backend.account.dto.ClientEvent;
import com.backend.account.messaging.ClientEventConsumer;
import com.backend.account.model.ClientProjection;
import com.backend.account.repository.*;
import java.util.*;
import org.junit.jupiter.api.*;
import tools.jackson.databind.json.JsonMapper;

class ClientEventConsumerTest {
  @Test
  void duplicateEventIsIgnored() {
    var clients = mock(ClientProjectionRepository.class);
    var processed = mock(ProcessedEventRepository.class);
    var json = JsonMapper.builder().build();
    UUID id = UUID.randomUUID();
    when(processed.existsById(id)).thenReturn(true);
    new ClientEventConsumer(clients, processed, json)
        .accept(json.writeValueAsString(new ClientEvent(1, id, 1L, 0, "Ana", true)));
    verifyNoInteractions(clients);
    verify(processed, never()).save(any());
  }

  @Test
  void staleSnapshotCannotReactivateClient() {
    var clients = mock(ClientProjectionRepository.class);
    var processed = mock(ProcessedEventRepository.class);
    var json = JsonMapper.builder().build();
    var c = new ClientProjection();
    c.id = 1L;
    c.eventVersion = 2;
    c.active = false;
    when(clients.findById(1L)).thenReturn(Optional.of(c));
    new ClientEventConsumer(clients, processed, json)
        .accept(json.writeValueAsString(new ClientEvent(1, UUID.randomUUID(), 1L, 1, "Ana", true)));
    assertThat(c.active).isFalse();
    verify(clients, never()).save(any());
    verify(processed).save(any());
  }
}
