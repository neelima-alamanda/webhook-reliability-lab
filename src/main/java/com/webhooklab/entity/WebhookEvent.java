package com.webhooklab.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "webhook_events", uniqueConstraints = {
        @UniqueConstraint(name = "uk_source_event", columnNames = {"source_id", "event_id"})
})
@Getter
@NoArgsConstructor
public class WebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_id", nullable = false)
    private WebhookSource source;

    @Setter
    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Setter
    @Column(nullable = false)
    private String resourceId;

    @Setter
    @Column(nullable = false)
    private Long sequence;

    @Setter
    @Column(nullable = false)
    private String type;

    @Setter
    @Column(nullable = false)
    private LocalDateTime occurredAt;

    @Setter
    @Column(nullable = false)
    private LocalDateTime receivedAt;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventStatus status;

    @Setter
    private String currentStatus;

    @Setter
    private Long lastSequence;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        WebhookEvent that = (WebhookEvent) o;

        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
