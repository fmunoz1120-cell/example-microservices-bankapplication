package com.backend.account.messaging;

import com.backend.account.dto.ClientEvent;
import com.backend.account.model.*;
import com.backend.account.repository.*;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Component
public class ClientEventConsumer {
  private final ClientProjectionRepository clients;
  private final ProcessedEventRepository processed;
  private final ObjectMapper json;

  public ClientEventConsumer(
      ClientProjectionRepository c, ProcessedEventRepository p, ObjectMapper j) {
    clients = c;
    processed = p;
    json = j;
  }

  @KafkaListener(
      topics = "${bank.events-topic}",
      autoStartup = "${bank.kafka-listener-enabled:true}")
  @Transactional
  public void accept(String payload) {
    ClientEvent e = json.readValue(payload, ClientEvent.class);
    if (e.schemaVersion() != 1
        || e.eventId() == null
        || e.clientId() == null
        || e.clientId() <= 0
        || e.name() == null
        || e.version() < 0) throw new IllegalArgumentException("Evento inválido");
    if (processed.existsById(e.eventId())) return;
    ClientProjection c = clients.findById(e.clientId()).orElse(null);
    if (c == null || e.version() > c.eventVersion) {
      if (c == null) {
        c = new ClientProjection();
        c.id = e.clientId();
      }
      c.name = e.name();
      c.active = e.active();
      c.eventVersion = e.version();
      clients.save(c);
    }
    processed.save(new ProcessedEvent(e.eventId()));
  }
}
