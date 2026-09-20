package com.example.foodrescue.volunteer;

import com.example.foodrescue.common.ForbiddenActionException;
import com.example.foodrescue.common.FoodCategory;
import com.example.foodrescue.common.FoodLot;
import com.example.foodrescue.common.LotStatus;
import com.example.foodrescue.common.LotStatusChanger;
import com.example.foodrescue.common.LotStore;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class VolunteerService {

    private static final BigDecimal BIG_LOT_THRESHOLD_KG = BigDecimal.valueOf(20);
    private static final int EARLY_ACCESS_MINUTES = 10;
    private static final int EARLY_ACCESS_MIN_SCORE = 90;
    private static final int RESERVE_DURATION_MINUTES = 30;

    private final VolunteerStore volunteerStore;
    private final LotStore lotStore;
    private final LotStatusChanger statusChanger;

    public VolunteerService(VolunteerStore volunteerStore, LotStore lotStore, LotStatusChanger statusChanger) {
        this.volunteerStore = volunteerStore;
        this.lotStore = lotStore;
        this.statusChanger = statusChanger;
    }

    public VolunteerProfile create(VolunteerRequest request) {
        VolunteerProfile profile = new VolunteerProfile();
        profile.setId(UUID.randomUUID());
        profile.setFullName(request.fullName());
        profile.setEmail(request.email());
        profile.setPhone(request.phone());
        profile.setTransportType(request.transportType());
        profile.setActivityZone(request.activityZone());
        return volunteerStore.save(profile);
    }

    public VolunteerProfile getById(UUID id) {
        return volunteerStore.getById(id);
    }

    /**
     * Резервування лоту за волонтером.
     * synchronized — щоб два волонтери не взяли один лот одночасно.
     */
    public synchronized FoodLot reserve(UUID lotId, ReservationRequest request) {
        FoodLot lot = lotStore.getById(lotId);
        VolunteerProfile volunteer = volunteerStore.getById(request.volunteerId());

        // Обмежений волонтер — лише BAKERY і GROCERY
        if (volunteer.isRestricted()) {
            FoodCategory category = lot.getCategory();
            if (category != FoodCategory.BAKERY && category != FoodCategory.GROCERY) {
                throw new VolunteerRestrictedException(volunteer.getId());
            }
        }

        // Великий лот: перші 10 хв після публікації — лише для волонтерів із показником понад 90
        if (isBigLot(lot) && isWithinEarlyAccessWindow(lot)
                && volunteer.getResponsibilityScore() <= EARLY_ACCESS_MIN_SCORE) {
            throw new ForbiddenActionException(
                    "Великий лот у перші " + EARLY_ACCESS_MINUTES
                            + " хв доступний лише волонтерам з показником понад " + EARLY_ACCESS_MIN_SCORE);
        }

        // Перехід PUBLISHED → RESERVED (409, якщо не PUBLISHED)
        statusChanger.transition(lot, LotStatus.RESERVED, "Зарезервовано");

        lot.setReservedByVolunteerId(request.volunteerId());
        lot.setReservedUntil(Instant.now().plus(RESERVE_DURATION_MINUTES, ChronoUnit.MINUTES));
        lotStore.save(lot);

        return lot;
    }

    /**
     * Скасування резервування: RESERVED → PUBLISHED, очищення полів резерву.
     */
    public FoodLot cancelReservation(UUID lotId) {
        FoodLot lot = lotStore.getById(lotId);

        // Перехід RESERVED → PUBLISHED (409, якщо не RESERVED)
        statusChanger.transition(lot, LotStatus.PUBLISHED, "Резерв скасовано");

        lot.setReservedByVolunteerId(null);
        lot.setReservedUntil(null);
        lotStore.save(lot);

        return lot;
    }

    private boolean isBigLot(FoodLot lot) {
        return lot.getTotalWeightKg().compareTo(BIG_LOT_THRESHOLD_KG) >= 0;
    }

    private boolean isWithinEarlyAccessWindow(FoodLot lot) {
        return lot.getPublishedAt() != null
                && Instant.now().isBefore(lot.getPublishedAt().plus(EARLY_ACCESS_MINUTES, ChronoUnit.MINUTES));
    }
}
