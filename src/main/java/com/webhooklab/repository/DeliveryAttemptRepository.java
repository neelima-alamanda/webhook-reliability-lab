package com.webhooklab.repository;

import com.webhooklab.entity.DeliveryAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeliveryAttemptRepository extends JpaRepository<DeliveryAttempt, Long> {

    List<DeliveryAttempt> findByEventId(Long eventId);

    List<DeliveryAttempt> findByEventIdOrderByIdAsc(Long eventId);
}
