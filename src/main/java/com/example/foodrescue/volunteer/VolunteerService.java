package com.example.foodrescue.volunteer;

import com.example.foodrescue.common.FoodLot;
import com.example.foodrescue.delivery.DeliveryOutcome;

import java.util.List;
import java.util.UUID;

public interface VolunteerService {

    VolunteerResponse create(VolunteerRequest request);

    VolunteerResponse getById(UUID id);

    List<VolunteerResponse> getAll();

    VolunteerResponse update(UUID id, VolunteerRequest request);

    void delete(UUID id);

    FoodLot reserve(UUID lotId, ReservationRequest request);

    FoodLot cancelReservation(UUID lotId);

    void recordOutcome(UUID volunteerId, DeliveryOutcome outcome, boolean latePickup);

    PreferredPointResponse addPreferredPoint(UUID volunteerId, UUID pointId);

    void removePreferredPoint(UUID volunteerId, UUID pointId);

    List<PreferredPointResponse> getPreferredPoints(UUID volunteerId);
}
