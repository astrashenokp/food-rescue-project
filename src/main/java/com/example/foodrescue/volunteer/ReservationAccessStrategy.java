package com.example.foodrescue.volunteer;

import com.example.foodrescue.common.FoodLot;

import java.time.Instant;

public interface ReservationAccessStrategy {

    VolunteerTier tier();

    boolean canReserve(FoodLot lot, Instant now);
}
