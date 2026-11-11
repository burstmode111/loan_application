package com.llms.loan_application.loan;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository outbox;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxRelay(OutboxEventRepository outbox,
                       KafkaTemplate<String, String> kafkaTemplate) {
        this.outbox = outbox;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 2000)
    public void publishPending() {
        List<OutboxEvent> events = outbox.findTop50ByStatusOrderByIdAsc("PENDING");
        if (!events.isEmpty()) {
            log.info("relay: picked {} pending events", events.size());
        }
        for (OutboxEvent e : events) {
            try {
                // set 5s to send the message to broker
                kafkaTemplate
                        .send("loan-events", String.valueOf(e.getAggregateId()), e.getPayload())
                        .get(5, TimeUnit.SECONDS);
                e.setStatus("SENT");
                log.info("relay: sent outbox id={} key={}", e.getId(), e.getAggregateId());
                outbox.save(e);
            } catch (Exception ex) {
                log.error("failed to publish outbox id={}", e.getId(), ex);
            }
        }
    }
}
