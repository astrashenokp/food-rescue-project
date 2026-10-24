package com.example.foodrescue.volunteer;

import com.example.foodrescue.common.LotResponse;
import com.example.foodrescue.delivery.DeliveryOutcome;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;
import java.util.UUID;

public interface VolunteerService {

    VolunteerResponse create(VolunteerRequest request);

    VolunteerResponse getById(UUID id);

    List<VolunteerResponse> getAll();

    VolunteerResponse update(UUID id, VolunteerRequest request);

    void delete(UUID id);

    @PreAuthorize("hasRole('VOLUNTEER')")
    void reserve(UUID lotId);

    @PreAuthorize("hasRole('VOLUNTEER')")
    void cancelReservation(UUID lotId);

    @PreAuthorize("hasRole('VOLUNTEER')")
    List<LotResponse> getAvailableLots();

    @PreAuthorize("hasRole('VOLUNTEER')")
    List<LotResponse> getReservations();

    void recordOutcome(UUID volunteerId, DeliveryOutcome outcome, boolean latePickup);

    PreferredPointResponse addPreferredPoint(UUID volunteerId, UUID pointId);

    void removePreferredPoint(UUID volunteerId, UUID pointId);

    List<PreferredPointResponse> getPreferredPoints(UUID volunteerId);
}
