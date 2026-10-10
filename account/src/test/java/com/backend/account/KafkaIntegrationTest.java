package com.backend.account;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

import com.backend.account.dto.ClientEvent;
import com.backend.account.repository.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = "bank.kafka-listener-enabled=true")
@ActiveProfiles("integration")
@Tag("integration")
class KafkaIntegrationTest {
  @Autowired KafkaTemplate<String, String> kafka;
  @Autowired ObjectMapper json;
  @Autowired ClientProjectionRepository clients;
  @Autowired ProcessedEventRepository processed;

  @Test
  void kafkaSynchronizesAndDeduplicatesClient() throws Exception {
    Long id = Math.abs(UUID.randomUUID().getMostSignificantBits());
    UUID eventId = UUID.randomUUID();
    String event =
        json.writeValueAsString(new ClientEvent(1, eventId, id, 1, "Kafka client", true));
    kafka.send("bank.clients.integration.v1", id.toString(), event).get(10, TimeUnit.SECONDS);
    await().atMost(20, TimeUnit.SECONDS).until(() -> clients.findById(id).isPresent());

    kafka.send("bank.clients.integration.v1", id.toString(), event).get(10, TimeUnit.SECONDS);
    var deleted = new ClientEvent(1, UUID.randomUUID(), id, 2, "Kafka client", false);
    kafka
        .send("bank.clients.integration.v1", id.toString(), json.writeValueAsString(deleted))
        .get(10, TimeUnit.SECONDS);
    await().atMost(20, TimeUnit.SECONDS).until(() -> !clients.findById(id).orElseThrow().active);

    assertThat(processed.existsById(eventId)).isTrue();
    assertThat(clients.findById(id).orElseThrow().eventVersion).isEqualTo(2);
  }
}
