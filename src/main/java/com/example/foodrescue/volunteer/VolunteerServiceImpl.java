package com.example.foodrescue.volunteer;

import com.example.foodrescue.common.FoodLot;
import com.example.foodrescue.common.LotNotFoundException;
import com.example.foodrescue.common.LotRepository;
import com.example.foodrescue.common.LotStatus;
import com.example.foodrescue.common.LotStatusChanger;
import com.example.foodrescue.common.SensitiveDataMasker;
import com.example.foodrescue.delivery.DeliveryOutcome;
import com.example.foodrescue.delivery.DestinationPoint;
import com.example.foodrescue.delivery.DestinationPointRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class VolunteerServiceImpl implements VolunteerService {

    private static final Logger log = LoggerFactory.getLogger(VolunteerServiceImpl.class);
    private static final int RESERVE_DURATION_MINUTES = 30;

    private final VolunteerRepository volunteerRepository;
    private final LotRepository lotRepository;
    private final LotStatusChanger statusChanger;
    private final DestinationPointRepository destinationPointRepository;
    private final Map<VolunteerTier, ReservationAccessStrategy> accessStrategies = new EnumMap<>(VolunteerTier.class);
    private final Clock clock;

    public VolunteerServiceImpl(VolunteerRepository volunteerRepository,
                                LotRepository lotRepository,
                                LotStatusChanger statusChanger,
                                DestinationPointRepository destinationPointRepository,
                                List<ReservationAccessStrategy> accessStrategies,
                                Clock clock) {
        this.volunteerRepository = volunteerRepository;
        this.lotRepository = lotRepository;
        this.statusChanger = statusChanger;
        this.destinationPointRepository = destinationPointRepository;
        accessStrategies.forEach(strategy -> this.accessStrategies.put(strategy.tier(), strategy));
        this.clock = clock;
    }

    @Override
    @Transactional
    public VolunteerResponse create(VolunteerRequest request) {
        if (volunteerRepository.existsByEmail(request.email())) {
            throw new DuplicateVolunteerException(request.email());
        }

        VolunteerProfile profile = new VolunteerProfile(UUID.randomUUID());
        profile.setFullName(request.fullName());
        profile.setEmail(request.email());
        profile.setPhone(request.phone());
        profile.setTransportType(request.transportType());
        profile.setActivityZone(request.activityZone());
        VolunteerResponse response = VolunteerResponse.from(volunteerRepository.save(profile));
        log.info("Зареєстровано волонтера {} з email {}", response.id(), SensitiveDataMasker.maskEmail(request.email()));
        return response;
    }

    @Override
    public VolunteerResponse getById(UUID id) {
        VolunteerProfile profile = volunteerRepository.findByIdWithPoints(id)
                .orElseThrow(() -> new VolunteerNotFoundException(id));
        return VolunteerResponse.from(profile);
    }

    @Override
    public List<VolunteerResponse> getAll() {
        return volunteerRepository.findAllWithPoints().stream()
                .map(VolunteerResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public VolunteerResponse update(UUID id, VolunteerRequest request) {
        VolunteerProfile profile = volunteerRepository.findByIdWithPoints(id)
                .orElseThrow(() -> new VolunteerNotFoundException(id));

        if (volunteerRepository.existsByEmailAndIdNot(request.email(), id)) {
            throw new DuplicateVolunteerException(request.email());
        }

        profile.setFullName(request.fullName());
        profile.setEmail(request.email());
        profile.setPhone(request.phone());
        profile.setTransportType(request.transportType());
        profile.setActivityZone(request.activityZone());
        VolunteerResponse response = VolunteerResponse.from(volunteerRepository.save(profile));
        log.info("Оновлено профіль волонтера {}", response.id());
        return response;
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        VolunteerProfile profile = volunteerRepository.findById(id)
                .orElseThrow(() -> new VolunteerNotFoundException(id));

        boolean hasActiveLots = lotRepository.existsByReservedByVolunteerIdAndStatusIn(
                id, EnumSet.of(LotStatus.RESERVED, LotStatus.PICKED_UP));
        if (hasActiveLots) {
            throw new VolunteerHasActiveLotsException(id);
        }

        volunteerRepository.delete(profile);
        log.info("Видалено волонтера {}", id);
    }

    /**
     * Резервування лоту за волонтером.
     * @Version на FoodLot дає оптимістичне блокування замість synchronized.
     */
    @Override
    @Transactional
    public FoodLot reserve(UUID lotId, ReservationRequest request) {
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
        log.info("Волонтер {} зарезервував лот {}", request.volunteerId(), lotId);

        return lot;
    }

    /**
     * Скасування резервування: RESERVED → PUBLISHED, очищення полів резерву.
     */
    @Override
    @Transactional
    public FoodLot cancelReservation(UUID lotId) {
        FoodLot lot = lotRepository.findById(lotId)
                .orElseThrow(() -> new LotNotFoundException(lotId));

        statusChanger.transition(lot, LotStatus.PUBLISHED, "Резерв скасовано");

        lot.setReservedByVolunteerId(null);
        lot.setReservedUntil(null);
        lotRepository.save(lot);
        log.info("Резерв скасовано на лоті {}", lotId);

        return lot;
    }

    /**
     * Оновлює лічильники волонтера атомарними UPDATE-запитами.
     * Без synchronized, без read-modify-write — потокобезпечно.
     */
    @Override
    @Transactional
    public void recordOutcome(UUID volunteerId, DeliveryOutcome outcome, boolean latePickup) {
        if (!volunteerRepository.existsById(volunteerId)) {
            throw new VolunteerNotFoundException(volunteerId);
        }

        switch (outcome) {
            case CONFIRMED -> volunteerRepository.incrementCompleted(volunteerId);
            case DISPUTED -> volunteerRepository.incrementNoShows(volunteerId);
        }

        if (latePickup) {
            volunteerRepository.incrementLatePickups(volunteerId);
        }
        log.info("Записано результат {} для волонтера {}, запізнення: {}", outcome, volunteerId, latePickup);
    }

    @Override
    @Transactional
    public PreferredPointResponse addPreferredPoint(UUID volunteerId, UUID pointId) {
        VolunteerProfile profile = volunteerRepository.findByIdWithPoints(volunteerId)
                .orElseThrow(() -> new VolunteerNotFoundException(volunteerId));
        DestinationPoint point = destinationPointRepository.findById(pointId)
                .orElseThrow(() -> new com.example.foodrescue.common.NotFoundException(
                        "Точку призначення " + pointId + " не знайдено"));

        profile.getPreferredPoints().add(point);
        volunteerRepository.save(profile);
        log.debug("Волонтер {} додав бажану точку {}", volunteerId, pointId);
        return new PreferredPointResponse(point.getId(), point.getName());
    }

    @Override
    @Transactional
    public void removePreferredPoint(UUID volunteerId, UUID pointId) {
        VolunteerProfile profile = volunteerRepository.findByIdWithPoints(volunteerId)
                .orElseThrow(() -> new VolunteerNotFoundException(volunteerId));

        boolean removed = profile.getPreferredPoints().removeIf(p -> p.getId().equals(pointId));
        if (!removed) {
            throw new com.example.foodrescue.common.NotFoundException(
                    "Точку " + pointId + " не знайдено у бажаних точках волонтера " + volunteerId);
        }

        volunteerRepository.save(profile);
        log.debug("Волонтер {} видалив бажану точку {}", volunteerId, pointId);
    }

    @Override
    public List<PreferredPointResponse> getPreferredPoints(UUID volunteerId) {
        VolunteerProfile profile = volunteerRepository.findByIdWithPoints(volunteerId)
                .orElseThrow(() -> new VolunteerNotFoundException(volunteerId));
        return profile.getPreferredPoints().stream()
                .map(p -> new PreferredPointResponse(p.getId(), p.getName()))
                .toList();
    }
}
