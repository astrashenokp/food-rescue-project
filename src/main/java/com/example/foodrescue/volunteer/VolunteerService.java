package com.example.foodrescue.volunteer;

import com.example.foodrescue.common.FoodLot;
import com.example.foodrescue.delivery.DeliveryOutcome;

import java.util.UUID;

public interface VolunteerService {

    VolunteerProfile create(VolunteerRequest request);

    VolunteerProfile getById(UUID id);

    FoodLot reserve(UUID lotId, ReservationRequest request);

    FoodLot cancelReservation(UUID lotId);

    void recordOutcome(UUID volunteerId, DeliveryOutcome outcome, boolean latePickup);
}
