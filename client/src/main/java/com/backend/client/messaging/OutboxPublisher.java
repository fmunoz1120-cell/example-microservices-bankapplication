package com.backend.client.messaging;

import com.backend.client.repository.OutboxRepository;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxPublisher {
  private final OutboxRepository repo;
  private final KafkaTemplate<String, String> kafka;
  private final String topic;

  public OutboxPublisher(
      OutboxRepository r,
      KafkaTemplate<String, String> k,
      @Value("${bank.events-topic}") String t) {
    repo = r;
    kafka = k;
    topic = t;
  }

  @Scheduled(
      fixedDelayString = "${bank.outbox-delay-ms:10000}",
      initialDelayString = "${bank.outbox-delay-ms:10000}")
  @Transactional(rollbackFor = Exception.class)
  public void publish() throws Exception {
    for (var e : repo.pending()) {
      kafka.send(topic, e.clientId.toString(), e.payload).get(10, TimeUnit.SECONDS);
      e.published = true;
    }
  }
}
