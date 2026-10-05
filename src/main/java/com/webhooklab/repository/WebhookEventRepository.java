package com.webhooklab.repository;

import com.webhooklab.entity.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WebhookEventRepository extends JpaRepository<WebhookEvent, Long> {

    Optional<WebhookEvent> findBySourceIdAndEventId(Long sourceId, String eventId);

    boolean existsBySourceIdAndEventId(Long sourceId, String eventId);
}
