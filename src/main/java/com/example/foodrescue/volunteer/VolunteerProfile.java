package com.example.foodrescue.volunteer;

import java.util.UUID;

public class VolunteerProfile {
    private UUID id;
    private String fullName;
    private String email;
    private String phone;
    private TransportType transportType;
    private String activityZone;
    private int completedDeliveries;
    private int latePickups;
    private int noShows;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
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
}
