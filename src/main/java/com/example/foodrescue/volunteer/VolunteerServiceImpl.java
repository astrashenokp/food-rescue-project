package com.example.foodrescue.volunteer;

import com.example.foodrescue.common.FoodLot;
import com.example.foodrescue.common.LotNotFoundException;
import com.example.foodrescue.common.LotRepository;
import com.example.foodrescue.common.LotStatus;
import com.example.foodrescue.common.LotStatusChanger;
import com.example.foodrescue.delivery.DeliveryOutcome;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class VolunteerServiceImpl implements VolunteerService {

    private static final int RESERVE_DURATION_MINUTES = 30;

    private final VolunteerRepository volunteerRepository;
    private final LotRepository lotRepository;
    private final LotStatusChanger statusChanger;
    private final Map<VolunteerTier, ReservationAccessStrategy> accessStrategies = new EnumMap<>(VolunteerTier.class);
    private final Clock clock;

    public VolunteerServiceImpl(VolunteerRepository volunteerRepository,
                                LotRepository lotRepository,
                                LotStatusChanger statusChanger,
                                List<ReservationAccessStrategy> accessStrategies,
                                Clock clock) {
        this.volunteerRepository = volunteerRepository;
        this.lotRepository = lotRepository;
        this.statusChanger = statusChanger;
        accessStrategies.forEach(strategy -> this.accessStrategies.put(strategy.tier(), strategy));
        this.clock = clock;
    }

    @Override
    public VolunteerProfile create(VolunteerRequest request) {
        VolunteerProfile profile = new VolunteerProfile();
        profile.setId(UUID.randomUUID());
        profile.setFullName(request.fullName());
        profile.setEmail(request.email());
        profile.setPhone(request.phone());
        profile.setTransportType(request.transportType());
        profile.setActivityZone(request.activityZone());
        return volunteerRepository.save(profile);
    }

    @Override
    public VolunteerProfile getById(UUID id) {
        return volunteerRepository.findById(id)
                .orElseThrow(() -> new VolunteerNotFoundException(id));
    }

    /**
     * Резервування лоту за волонтером.
     * synchronized — щоб два волонтери не взяли один лот одночасно.
     */
    @Override
    public synchronized FoodLot reserve(UUID lotId, ReservationRequest request) {
        FoodLot lot = lotRepository.findById(lotId)
                .orElseThrow(() -> new LotNotFoundException(lotId));
        VolunteerProfile volunteer = volunteerRepository.findById(request.volunteerId())
                .orElseThrow(() -> new VolunteerNotFoundException(request.volunteerId()));

        VolunteerTier tier = volunteer.getTier();
        ReservationAccessStrategy strategy = accessStrategies.get(tier);
        Instant now = Instant.now(clock);

        if (!strategy.canReserve(lot, now)) {
            throw new ReservationDeniedException(
                    "Волонтер " + volunteer.getId() + " (рівень " + tier + ") не може зарезервувати лот " + lotId);
        }

        // Перехід PUBLISHED → RESERVED (422, якщо перехід недозволений)
        statusChanger.transition(lot, LotStatus.RESERVED, "Зарезервовано");

        lot.setReservedByVolunteerId(request.volunteerId());
        lot.setReservedUntil(now.plus(RESERVE_DURATION_MINUTES, ChronoUnit.MINUTES));
        lotRepository.save(lot);

        return lot;
    }

    /**
     * Скасування резервування: RESERVED → PUBLISHED, очищення полів резерву.
     */
    @Override
    public FoodLot cancelReservation(UUID lotId) {
        FoodLot lot = lotRepository.findById(lotId)
                .orElseThrow(() -> new LotNotFoundException(lotId));

        statusChanger.transition(lot, LotStatus.PUBLISHED, "Резерв скасовано");

        lot.setReservedByVolunteerId(null);
        lot.setReservedUntil(null);
        lotRepository.save(lot);

        return lot;
    }

    /**
     * Оновлює лічильники волонтера після завершення доставки.
     * synchronized — викликається з іншого потоку слухачем.
     */
    @Override
    public synchronized void recordOutcome(UUID volunteerId, DeliveryOutcome outcome, boolean latePickup) {
        VolunteerProfile volunteer = volunteerRepository.findById(volunteerId)
                .orElseThrow(() -> new VolunteerNotFoundException(volunteerId));

        switch (outcome) {
            case CONFIRMED -> volunteer.setCompletedDeliveries(volunteer.getCompletedDeliveries() + 1);
            case DISPUTED -> volunteer.setNoShows(volunteer.getNoShows() + 1);
        }

        if (latePickup) {
            volunteer.setLatePickups(volunteer.getLatePickups() + 1);
        }

        volunteerRepository.save(volunteer);
    }
}
