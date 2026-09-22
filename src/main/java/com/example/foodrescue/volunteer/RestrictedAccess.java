package com.example.foodrescue.volunteer;

import com.example.foodrescue.common.FoodCategory;
import com.example.foodrescue.common.FoodLot;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class RestrictedAccess implements ReservationAccessStrategy {

    private static final BigDecimal BIG_LOT_THRESHOLD_KG = BigDecimal.valueOf(20);
    private static final int EARLY_ACCESS_MINUTES = 10;

    @Override
    public VolunteerTier tier() {
        return VolunteerTier.RESTRICTED;
    }

    @Override
    public boolean canReserve(FoodLot lot, Instant now) {
        FoodCategory category = lot.getCategory();
        if (category != FoodCategory.BAKERY && category != FoodCategory.GROCERY) {
            return false;
        }
        return !(isBigLot(lot) && isWithinEarlyAccessWindow(lot, now));
    }

    private boolean isBigLot(FoodLot lot) {
        return lot.getTotalWeightKg().compareTo(BIG_LOT_THRESHOLD_KG) >= 0;
    }

    private boolean isWithinEarlyAccessWindow(FoodLot lot, Instant now) {
        return lot.getPublishedAt() != null
                && now.isBefore(lot.getPublishedAt().plus(EARLY_ACCESS_MINUTES, ChronoUnit.MINUTES));
    }
}
