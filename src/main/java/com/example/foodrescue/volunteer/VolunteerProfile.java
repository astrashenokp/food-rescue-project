package com.example.foodrescue.volunteer;

import com.example.foodrescue.delivery.DestinationPoint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "volunteers")
public class VolunteerProfile {

    @Id
    private UUID id;

    @Version
    private Long version;

    @Column(nullable = false, length = 100)
    private String fullName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false, length = 15)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TransportType transportType;

    @Column(nullable = false, length = 100)
    private String activityZone;

    @Column(nullable = false)
    private int completedDeliveries;

    @Column(nullable = false)
    private int latePickups;

    @Column(nullable = false)
    private int noShows;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "volunteer_points",
            joinColumns = @JoinColumn(name = "volunteer_id"),
            inverseJoinColumns = @JoinColumn(name = "point_id"))
    private Set<DestinationPoint> preferredPoints = new HashSet<>();

    protected VolunteerProfile() {
    }

    public VolunteerProfile(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }

    public Long getVersion() {
        return version;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public TransportType getTransportType() {
        return transportType;
    }

    public void setTransportType(TransportType transportType) {
        this.transportType = transportType;
    }

    public String getActivityZone() {
        return activityZone;
    }

    public void setActivityZone(String activityZone) {
        this.activityZone = activityZone;
    }

    public int getCompletedDeliveries() {
        return completedDeliveries;
    }

    public void setCompletedDeliveries(int completedDeliveries) {
        this.completedDeliveries = completedDeliveries;
    }

    public int getLatePickups() {
        return latePickups;
    }

    public void setLatePickups(int latePickups) {
        this.latePickups = latePickups;
    }

    public int getNoShows() {
        return noShows;
    }

    public void setNoShows(int noShows) {
        this.noShows = noShows;
    }

    public Set<DestinationPoint> getPreferredPoints() {
        return preferredPoints;
    }
    /**
     * Показник відповідальності: % успішних − 2 × запізнення − 10 × зриви.
     * % успішних = completed / (completed + noShows) × 100; без жодної доставки — 100.
     * Результат у межах 0-100.
     */
    public int getResponsibilityScore() {
        int total = completedDeliveries + noShows;
        double successPercentage = (total == 0) ? 100.0 : (double) completedDeliveries / total * 100;
        double score = successPercentage - 2.0 * latePickups - 10.0 * noShows;
        return (int) Math.round(Math.max(0, Math.min(100, score)));
    }

    /**
     * Рівень волонтера за відповідальністю.
     */
    public VolunteerTier getTier() {
        if (isRestricted()) {
            return VolunteerTier.RESTRICTED;
        }
        if (getResponsibilityScore() > 90) {
            return VolunteerTier.TRUSTED;
        }
        return VolunteerTier.STANDARD;
    }

    /**
     * Обмежений волонтер: понад 3 зриви АБО понад 15% запізнень.
     * Може брати лише BAKERY і GROCERY.
     */
    public boolean isRestricted() {
        if (noShows > 3) {
            return true;
        }
        int total = completedDeliveries + noShows;
        return total > 0 && (double) latePickups / total > 0.15;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VolunteerProfile other)) return false;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
