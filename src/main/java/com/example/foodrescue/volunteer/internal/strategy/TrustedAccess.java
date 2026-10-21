package com.example.foodrescue.volunteer.internal.strategy;

import com.example.foodrescue.common.FoodLot;
import com.example.foodrescue.volunteer.internal.VolunteerTier;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class TrustedAccess implements ReservationAccessStrategy {

    @Override
    public VolunteerTier tier() {
        return VolunteerTier.TRUSTED;
    }

    @Override
    public boolean canReserve(FoodLot lot, Instant now) {
        return true;
    }
}
