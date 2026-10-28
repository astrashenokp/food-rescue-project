package com.example.foodrescue.volunteer.internal;

import com.example.foodrescue.common.CurrentUser;
import com.example.foodrescue.common.FoodLot;
import com.example.foodrescue.common.ForbiddenActionException;
import com.example.foodrescue.common.InvalidLotStateException;
import com.example.foodrescue.common.LotNotFoundException;
import com.example.foodrescue.common.LotRepository;
import com.example.foodrescue.common.LotResponse;
import com.example.foodrescue.common.LotStatus;
import com.example.foodrescue.common.LotStatusChanger;
import com.example.foodrescue.common.NotFoundException;
import com.example.foodrescue.common.SensitiveDataMasker;
import com.example.foodrescue.delivery.DeliveryOutcome;
import com.example.foodrescue.delivery.DestinationPoint;
import com.example.foodrescue.delivery.DestinationPointRepository;
import com.example.foodrescue.volunteer.PreferredPointResponse;
import com.example.foodrescue.volunteer.VolunteerRequest;
import com.example.foodrescue.volunteer.VolunteerResponse;
import com.example.foodrescue.volunteer.VolunteerService;
import com.example.foodrescue.volunteer.internal.exception.DuplicateVolunteerException;
import com.example.foodrescue.volunteer.internal.exception.ReservationDeniedException;
import com.example.foodrescue.volunteer.internal.exception.VolunteerHasActiveLotsException;
import com.example.foodrescue.volunteer.internal.exception.VolunteerNotFoundException;
import com.example.foodrescue.volunteer.internal.strategy.ReservationAccessStrategy;
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
    private final CurrentUser currentUser;

    public VolunteerServiceImpl(VolunteerRepository volunteerRepository,
                                LotRepository lotRepository,
                                LotStatusChanger statusChanger,
                                DestinationPointRepository destinationPointRepository,
                                List<ReservationAccessStrategy> accessStrategies,
                                Clock clock,
                                CurrentUser currentUser) {
        this.volunteerRepository = volunteerRepository;
        this.lotRepository = lotRepository;
        this.statusChanger = statusChanger;
        this.destinationPointRepository = destinationPointRepository;
        accessStrategies.forEach(strategy -> this.accessStrategies.put(strategy.tier(), strategy));
        this.clock = clock;
        this.currentUser = currentUser;
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

    @Override
    @Transactional
    public void reserve(UUID lotId) {
        UUID volunteerId = currentVolunteerId();
        FoodLot lot = lotRepository.findById(lotId)
                .orElseThrow(() -> new LotNotFoundException(lotId));
        VolunteerProfile volunteer = volunteerRepository.findById(volunteerId)
                .orElseThrow(() -> new VolunteerNotFoundException(volunteerId));

        VolunteerTier tier = volunteer.getTier();
        ReservationAccessStrategy strategy = accessStrategies.get(tier);
        Instant now = Instant.now(clock);

        if (!strategy.canReserve(lot, now)) {
            throw new ReservationDeniedException(
                    "Волонтер " + volunteer.getId() + " (рівень " + tier + ") не може зарезервувати лот " + lotId);
        }

        statusChanger.transition(lot, LotStatus.RESERVED, "Зарезервовано");

        lot.setReservedByVolunteerId(volunteerId);
        lot.setReservedUntil(now.plus(RESERVE_DURATION_MINUTES, ChronoUnit.MINUTES));
        lotRepository.save(lot);
        log.info("Волонтер {} зарезервував лот {}", volunteerId, lotId);
    }

    @Override
    @Transactional
    public void cancelReservation(UUID lotId) {
        FoodLot lot = lotRepository.findById(lotId)
                .orElseThrow(() -> new LotNotFoundException(lotId));
        if (lot.getStatus() != LotStatus.RESERVED) {
            throw new InvalidLotStateException("Лот " + lotId + " не зарезервовано, скасовувати нічого");
        }
        if (!currentVolunteerId().equals(lot.getReservedByVolunteerId())) {
            throw new ForbiddenActionException("Лот зарезервовано іншим волонтером");
        }

        statusChanger.transition(lot, LotStatus.PUBLISHED, "Резерв скасовано");

        lot.setReservedByVolunteerId(null);
        lot.setReservedUntil(null);
        lotRepository.save(lot);
        log.info("Резерв скасовано на лоті {}", lotId);
    }

    @Override
    public List<LotResponse> getAvailableLots() {
        return lotRepository.findAllByStatusWithItems(LotStatus.PUBLISHED).stream()
                .map(LotResponse::from)
                .toList();
    }

    @Override
    public List<LotResponse> getReservations() {
        return lotRepository.findAllByStatusWithItems(LotStatus.RESERVED).stream()
                .map(LotResponse::from)
                .toList();
    }

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
                .orElseThrow(() -> new NotFoundException(
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
            throw new NotFoundException(
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

    private UUID currentVolunteerId() {
        UUID profileId = currentUser.profileId();
        if (profileId == null) {
            throw new ForbiddenActionException("Користувач не пов'язаний з профілем волонтера");
        }
        return profileId;
    }
}
