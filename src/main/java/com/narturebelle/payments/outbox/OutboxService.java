package com.narturebelle.payments.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    /** Call inside the transaction making the change: a failure here must roll that change back too. */
    public void enqueue(String eventId, String eventType, Object payload) {
        if (outboxEventRepository.existsByEventId(eventId)) {
            return;
        }
        OutboxEvent event = new OutboxEvent();
        event.setEventId(eventId);
        event.setEventType(eventType);
        try {
            event.setPayload(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize outbox event " + eventId, e);
        }
        outboxEventRepository.save(event);
    }
}
