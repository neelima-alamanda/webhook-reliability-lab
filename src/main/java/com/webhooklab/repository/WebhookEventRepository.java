package com.webhooklab.repository;

import com.webhooklab.entity.EventStatus;
import com.webhooklab.entity.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WebhookEventRepository extends JpaRepository<WebhookEvent, Long> {

    Optional<WebhookEvent> findBySourceIdAndEventId(Long sourceId, String eventId);

    boolean existsBySourceIdAndEventId(Long sourceId, String eventId);

    List<WebhookEvent> findBySourceIdAndStatusOrderByIdDesc(Long sourceId, EventStatus status);

    List<WebhookEvent> findBySourceIdOrderByIdDesc(Long sourceId);

    List<WebhookEvent> findBySourceOwnerIdAndStatusOrderByIdDesc(Long ownerId, EventStatus status);

    List<WebhookEvent> findBySourceOwnerIdOrderByIdDesc(Long ownerId);
}
