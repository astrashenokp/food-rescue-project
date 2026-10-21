package com.example.foodrescue.lot;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "donor_stats")
public class DonorStats {

    @Id
    private UUID donorOrgId;

    @Version
    private Long version;

    private int confirmedLots;

    private int disputedLots;

    protected DonorStats() {
    }

    public DonorStats(UUID donorOrgId, int confirmedLots, int disputedLots) {
        this.donorOrgId = donorOrgId;
        this.confirmedLots = confirmedLots;
        this.disputedLots = disputedLots;
    }

    public UUID getDonorOrgId() {
        return donorOrgId;
    }

    public int getConfirmedLots() {
        return confirmedLots;
    }

    public int getDisputedLots() {
        return disputedLots;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DonorStats other)) return false;
        return Objects.equals(donorOrgId, other.donorOrgId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(donorOrgId);
    }
}
