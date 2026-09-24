package com.example.foodrescue.common;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "lot_status_history")
public class LotStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    @Column(nullable = false)
    private UUID lotId;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private LotStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LotStatus toStatus;

    @Column(nullable = false)
    private Instant changedAt;

    @Column(length = 300)
    private String comment;

    protected LotStatusHistory() {
    }

    public LotStatusHistory(UUID lotId, LotStatus fromStatus, LotStatus toStatus, Instant changedAt, String comment) {
        this.lotId = lotId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedAt = changedAt;
        this.comment = comment;
    }

    public Long getId() {
        return id;
    }

    public UUID getLotId() {
        return lotId;
    }

    public LotStatus getFromStatus() {
        return fromStatus;
    }

    public LotStatus getToStatus() {
        return toStatus;
    }

    public Instant getChangedAt() {
        return changedAt;
    }

    public String getComment() {
        return comment;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LotStatusHistory other)) return false;
        return id != null && Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
