package com.example.foodrescue.delivery;

import com.example.foodrescue.common.FoodCategory;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "destination_points")
public class DestinationPoint {

    @Id
    private UUID id;

    @Version
    private Long version;

    @Column(nullable = false)
    private UUID organizationId;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(nullable = false, length = 200)
    private String address;

    @Column(nullable = false, length = 11)
    private String workingHours;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "destination_point_categories", joinColumns = @JoinColumn(name = "point_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 20)
    private Set<FoodCategory> acceptedCategories = new HashSet<>();

    protected DestinationPoint() {
    }

    public DestinationPoint(UUID id, UUID organizationId, String name, String address, String workingHours,
                            Set<FoodCategory> acceptedCategories) {
        this.id = id;
        this.organizationId = organizationId;
        this.name = name;
        this.address = address;
        this.workingHours = workingHours;
        this.acceptedCategories = new HashSet<>(acceptedCategories);
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(UUID organizationId) {
        this.organizationId = organizationId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getWorkingHours() {
        return workingHours;
    }

    public void setWorkingHours(String workingHours) {
        this.workingHours = workingHours;
    }

    public Set<FoodCategory> getAcceptedCategories() {
        return acceptedCategories;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DestinationPoint other)) return false;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
