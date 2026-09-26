package com.example.foodrescue.delivery;

import com.example.foodrescue.common.FoodLot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "deliveries")
public class Delivery {

    @Id
    private UUID id;

    @Version
    private Long version;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lot_id", nullable = false, unique = true)
    private FoodLot lot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_point_id")
    private DestinationPoint destinationPoint;

    @Column(nullable = false)
    private UUID volunteerId;

    @Column(nullable = false)
    private Instant pickedUpAt;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal pickupWeightKg;

    @Column(nullable = false)
    private boolean latePickup;

    private Instant deliveredAt;

    @Column(length = 6)
    private String confirmationCode;

    private Instant confirmedAt;

    @Column(precision = 10, scale = 2)
    private BigDecimal receivedWeightKg;

    protected Delivery() {
    }

    public Delivery(UUID id,
                    FoodLot lot,
                    UUID volunteerId,
                    Instant pickedUpAt,
                    BigDecimal pickupWeightKg,
                    boolean latePickup) {
        this.id = id;
        this.lot = lot;
        this.volunteerId = volunteerId;
        this.pickedUpAt = pickedUpAt;
        this.pickupWeightKg = pickupWeightKg;
        this.latePickup = latePickup;
    }

    public UUID getId() {
        return id;
    }

    public FoodLot getLot() {
        return lot;
    }

    public DestinationPoint getDestinationPoint() {
        return destinationPoint;
    }

    public void setDestinationPoint(DestinationPoint destinationPoint) {
        this.destinationPoint = destinationPoint;
    }

    public UUID getVolunteerId() {
        return volunteerId;
    }

    public Instant getPickedUpAt() {
        return pickedUpAt;
    }

    public BigDecimal getPickupWeightKg() {
        return pickupWeightKg;
    }

    public boolean isLatePickup() {
        return latePickup;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public void setDeliveredAt(Instant deliveredAt) {
        this.deliveredAt = deliveredAt;
    }

    public String getConfirmationCode() {
        return confirmationCode;
    }

    public void setConfirmationCode(String confirmationCode) {
        this.confirmationCode = confirmationCode;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(Instant confirmedAt) {
        this.confirmedAt = confirmedAt;
    }

    public BigDecimal getReceivedWeightKg() {
        return receivedWeightKg;
    }

    public void setReceivedWeightKg(BigDecimal receivedWeightKg) {
        this.receivedWeightKg = receivedWeightKg;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Delivery other)) return false;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
