package com.narturebelle.payments.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {
    boolean existsByEventId(String eventId);
}
