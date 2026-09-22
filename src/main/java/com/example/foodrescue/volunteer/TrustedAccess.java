package com.example.foodrescue.volunteer;

import com.example.foodrescue.common.FoodLot;
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
