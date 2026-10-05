package com.webhooklab.repository;

import com.webhooklab.entity.WebhookSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WebhookSourceRepository extends JpaRepository<WebhookSource, Long> {

    List<WebhookSource> findByOwnerId(Long ownerId);
}
