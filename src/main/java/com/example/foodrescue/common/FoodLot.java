package com.example.foodrescue.common;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "food_lots")
public class FoodLot {

    @Id
    private UUID id;

    @Version
    private Long version;

    @Column(nullable = false)
    private UUID donorOrgId;

    @Column(nullable = false, length = 100)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FoodCategory category;

    @OneToMany(mappedBy = "lot", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<FoodItem> items = new ArrayList<>();

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalWeightKg;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StorageCondition storageCondition;

    @Column(nullable = false, length = 200)
    private String pickupAddress;

    @Column(nullable = false)
    private Instant pickupFrom;

    @Column(nullable = false)
    private Instant pickupTo;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant publishedAt;

    private UUID reservedByVolunteerId;

    private Instant reservedUntil;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LotStatus status;

    protected FoodLot() {
    }

    public FoodLot(UUID id) {
        this.id = id;
    }

    public void addItem(FoodItem item) {
        items.add(item);
        item.setLot(this);
    }

    public void removeItem(FoodItem item) {
        items.remove(item);
        item.setLot(null);
    }

    public void replaceItems(List<FoodItem> newItems) {
        items.clear();
        newItems.forEach(this::addItem);
    }

    public UUID getId() {
        return id;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FoodLot other)) return false;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
