package com.example.foodrescue.volunteer;

import java.util.List;
import java.util.UUID;

public record VolunteerResponse(
        UUID id,
        String fullName,
        String email,
        String phone,
        TransportType transportType,
        String activityZone,
        int responsibilityScore,
        boolean restricted,
        int completedDeliveries,
        int latePickups,
        int noShows,
        List<PreferredPointResponse> preferredPoints
) {

    public static VolunteerResponse from(VolunteerProfile profile) {
        List<PreferredPointResponse> points = profile.getPreferredPoints().stream()
                .map(p -> new PreferredPointResponse(p.getId(), p.getName()))
                .toList();

        return new VolunteerResponse(
                profile.getId(),
                profile.getFullName(),
                profile.getEmail(),
                profile.getPhone(),
                profile.getTransportType(),
                profile.getActivityZone(),
                profile.getResponsibilityScore(),
                profile.isRestricted(),
                profile.getCompletedDeliveries(),
                profile.getLatePickups(),
                profile.getNoShows(),
                points);
    }
}
