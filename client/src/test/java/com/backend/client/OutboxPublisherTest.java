package com.backend.client;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.backend.client.messaging.OutboxPublisher;
import com.backend.client.model.OutboxEvent;
import com.backend.client.repository.OutboxRepository;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

class OutboxPublisherTest {
  @Test
  @SuppressWarnings("unchecked")
  void marksPublishedOnlyAfterAcknowledgement() throws Exception {
    var repo = mock(OutboxRepository.class);
    KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    var e = new OutboxEvent();
    e.id = UUID.randomUUID();
    e.clientId = 7L;
    e.payload = "event";
    when(repo.pending()).thenReturn(List.of(e));
    when(kafka.send("events", "7", "event")).thenReturn(CompletableFuture.completedFuture(null));
    new OutboxPublisher(repo, kafka, "events").publish();
    assertThat(e.published).isTrue();
    verify(kafka).send("events", "7", "event");
  }

  @Test
  @SuppressWarnings("unchecked")
  void failedSendKeepsEventPending() {
    var repo = mock(OutboxRepository.class);
    KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    var e = new OutboxEvent();
    e.clientId = 7L;
    e.payload = "event";
    when(repo.pending()).thenReturn(List.of(e));
    when(kafka.send("events", "7", "event"))
        .thenReturn(
            CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));
    assertThatThrownBy(() -> new OutboxPublisher(repo, kafka, "events").publish())
        .isInstanceOf(ExecutionException.class);
    assertThat(e.published).isFalse();
  }
}
