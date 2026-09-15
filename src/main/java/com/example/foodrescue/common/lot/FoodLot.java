package com.example.foodrescue.common.lot;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class FoodLot {

    private UUID id;
    private UUID donorOrgId;
    private String title;
    private FoodCategory category;
    private List<FoodItem> items;
    private BigDecimal totalWeightKg;
    private StorageCondition storageCondition;
    private String pickupAddress;
    private Instant pickupFrom;
    private Instant pickupTo;
    private Instant createdAt;
    private Instant publishedAt;
    private UUID reservedByVolunteerId;
    private Instant reservedUntil;
    private LotStatus status;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getDonorOrgId() {
        return donorOrgId;
    }

    public void setDonorOrgId(UUID donorOrgId) {
        this.donorOrgId = donorOrgId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public FoodCategory getCategory() {
        return category;
    }

    public void setCategory(FoodCategory category) {
        this.category = category;
    }

    public List<FoodItem> getItems() {
        return items;
    }

    public void setItems(List<FoodItem> items) {
        this.items = items;
    }

    public BigDecimal getTotalWeightKg() {
        return totalWeightKg;
    }

    public void setTotalWeightKg(BigDecimal totalWeightKg) {
        this.totalWeightKg = totalWeightKg;
    }

    public StorageCondition getStorageCondition() {
        return storageCondition;
    }

    public void setStorageCondition(StorageCondition storageCondition) {
        this.storageCondition = storageCondition;
    }

    public String getPickupAddress() {
        return pickupAddress;
    }

    public void setPickupAddress(String pickupAddress) {
        this.pickupAddress = pickupAddress;
    }

    public Instant getPickupFrom() {
        return pickupFrom;
    }

    public void setPickupFrom(Instant pickupFrom) {
        this.pickupFrom = pickupFrom;
    }

    public Instant getPickupTo() {
        return pickupTo;
    }

    public void setPickupTo(Instant pickupTo) {
        this.pickupTo = pickupTo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public UUID getReservedByVolunteerId() {
        return reservedByVolunteerId;
    }

    public void setReservedByVolunteerId(UUID reservedByVolunteerId) {
        this.reservedByVolunteerId = reservedByVolunteerId;
    }

    public Instant getReservedUntil() {
        return reservedUntil;
    }

    public void setReservedUntil(Instant reservedUntil) {
        this.reservedUntil = reservedUntil;
    }

    public LotStatus getStatus() {
        return status;
    }

    public void setStatus(LotStatus status) {
        this.status = status;
    }
}
