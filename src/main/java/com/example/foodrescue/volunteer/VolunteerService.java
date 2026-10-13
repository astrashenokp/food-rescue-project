package com.example.foodrescue.volunteer;

import com.example.foodrescue.common.FoodLot;
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
    FoodLot reserve(UUID lotId, ReservationRequest request);

    @PreAuthorize("hasRole('VOLUNTEER')")
    FoodLot cancelReservation(UUID lotId);

    void recordOutcome(UUID volunteerId, DeliveryOutcome outcome, boolean latePickup);

    PreferredPointResponse addPreferredPoint(UUID volunteerId, UUID pointId);

    void removePreferredPoint(UUID volunteerId, UUID pointId);

    List<PreferredPointResponse> getPreferredPoints(UUID volunteerId);
}
