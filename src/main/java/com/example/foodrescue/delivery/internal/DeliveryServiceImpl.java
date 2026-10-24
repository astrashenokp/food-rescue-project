package com.example.foodrescue.delivery.internal;

import com.example.foodrescue.common.CurrentUser;
import com.example.foodrescue.common.ForbiddenActionException;
import com.example.foodrescue.common.FoodCategory;
import com.example.foodrescue.common.FoodLot;
import com.example.foodrescue.common.LotNotFoundException;
import com.example.foodrescue.common.LotResponse;
import com.example.foodrescue.common.LotRepository;
import com.example.foodrescue.common.LotStatus;
import com.example.foodrescue.common.LotStatusChanger;
import com.example.foodrescue.common.LotStatusHistory;
import com.example.foodrescue.common.StatusHistoryRecorder;
import com.example.foodrescue.delivery.*;
import com.example.foodrescue.delivery.internal.exception.*;
import com.example.foodrescue.delivery.internal.strategy.WeightToleranceStrategy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.security.SecureRandom;

@Service
public class DeliveryServiceImpl implements DeliveryService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryServiceImpl.class);

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final int MAX_CONFIRMATION_ATTEMPTS = 5;
    private static final SecureRandom CODE_RANDOM = new SecureRandom();

    private final LotRepository lotRepository;
    private final LotStatusChanger statusChanger;
    private final StatusHistoryRecorder historyRecorder;
    private final DestinationPointRepository destinationPointRepository;
    private final DeliveryRepository deliveryRepository;
    private final Map<FoodCategory, WeightToleranceStrategy> weightToleranceStrategies = new EnumMap<>(FoodCategory.class);
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final CurrentUser currentUser;

    public DeliveryServiceImpl(
            LotRepository lotRepository,
            LotStatusChanger statusChanger,
            StatusHistoryRecorder historyRecorder,
            DestinationPointRepository destinationPointRepository,
            DeliveryRepository deliveryRepository,
            List<WeightToleranceStrategy> weightToleranceStrategies,
            ApplicationEventPublisher eventPublisher,
            Clock clock,
            CurrentUser currentUser) {
        this.lotRepository = lotRepository;
        this.statusChanger = statusChanger;
        this.historyRecorder = historyRecorder;
        this.destinationPointRepository = destinationPointRepository;
        this.deliveryRepository = deliveryRepository;
        weightToleranceStrategies.forEach(strategy -> this.weightToleranceStrategies.put(strategy.category(), strategy));
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.currentUser = currentUser;
    }

    @Override
    @Transactional
    public DestinationPointResponse createDestinationPoint(DestinationPointRequest request) {
        if (destinationPointRepository.existsByName(request.name())) {
            throw new DuplicateDestinationPointException(request.name());
        }

        DestinationPoint point = new DestinationPoint(
                UUID.randomUUID(),
                request.organizationId(),
                request.name(),
                request.address(),
                request.workingHours(),
                request.acceptedCategories());

        return DestinationPointResponse.from(destinationPointRepository.save(point));
    }

    @Override
    @Transactional(readOnly = true)
    public List<DestinationPointResponse> getDestinationPoints() {
        return destinationPointRepository.findAllWithCategories().stream()
                .map(DestinationPointResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public DestinationPointResponse getDestinationPoint(UUID id) {
        DestinationPoint point = destinationPointRepository.findByIdWithCategories(id)
                .orElseThrow(() -> new DestinationPointNotFoundException(id));
        return DestinationPointResponse.from(point);
    }

    @Override
    @Transactional
    public DestinationPointResponse updateDestinationPoint(UUID id, DestinationPointRequest request) {
        DestinationPoint point = destinationPointRepository.findByIdWithCategories(id)
                .orElseThrow(() -> new DestinationPointNotFoundException(id));

        if (destinationPointRepository.existsByNameAndIdNot(request.name(), id)) {
            throw new DuplicateDestinationPointException(request.name());
        }

        point.setOrganizationId(request.organizationId());
        point.setName(request.name());
        point.setAddress(request.address());
        point.setWorkingHours(request.workingHours());
        point.getAcceptedCategories().clear();
        point.getAcceptedCategories().addAll(request.acceptedCategories());

        return DestinationPointResponse.from(destinationPointRepository.save(point));
    }

    @Override
    @Transactional
    public void deleteDestinationPoint(UUID id) {
        DestinationPoint point = destinationPointRepository.findById(id)
                .orElseThrow(() -> new DestinationPointNotFoundException(id));

        if (deliveryRepository.existsByDestinationPointId(id)) {
            throw new DestinationPointInUseException(id);
        }

        destinationPointRepository.delete(point);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LotResponse> getReservedLots() {
        return lotRepository.findAllByStatusWithItems(LotStatus.RESERVED).stream()
                .map(LotResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public LotResponse getLotOverview(UUID lotId) {
        return lotRepository.findByIdWithItems(lotId)
                .map(LotResponse::from)
                .orElseThrow(() -> new LotNotFoundException(lotId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeliveryResponse> getDeliveries() {
        List<Delivery> deliveries = isCoordinator()
                ? deliveryRepository.findAllWithLotAndPoint()
                : deliveryRepository.findAllByVolunteerIdWithLotAndPoint(currentUser.profileId());
        return deliveries.stream().map(DeliveryResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public DeliveryResponse getDelivery(UUID lotId) {
        Delivery delivery = deliveryRepository.findByLotIdWithLot(lotId)
                .orElseThrow(() -> new DeliveryNotFoundException(lotId));
        verifyDeliveryOwner(delivery);
        return DeliveryResponse.from(delivery);
    }

    @Override
    @Transactional
    public DeliveryResponse pickup(UUID lotId, PickupRequest request) {
        FoodLot lot = lotRepository.findById(lotId)
                .orElseThrow(() -> new LotNotFoundException(lotId));
        if (!Objects.equals(currentUser.profileId(), lot.getReservedByVolunteerId())
                || lot.getReservedByVolunteerId() == null) {
            throw new ForbiddenActionException("Лот зарезервовано іншим волонтером");
        }
        Instant now = Instant.now(clock);
        boolean latePickup = lot.getReservedUntil() != null && now.isAfter(lot.getReservedUntil());

        statusChanger.transition(lot, LotStatus.PICKED_UP, "Лот передано волонтеру");

        Delivery delivery = new Delivery(
                UUID.randomUUID(),
                lot,
                lot.getReservedByVolunteerId(),
                now,
                request.actualWeightKg(),
                latePickup);

        deliveryRepository.save(delivery);
        lotRepository.save(lot);

        return DeliveryResponse.from(delivery);
    }

    @Override
    @Transactional
    public DeliveryResponse deliver(UUID lotId, DeliveryRequest request) {
        Delivery delivery = deliveryRepository.findByLotIdWithLot(lotId)
                .orElseThrow(() -> new DeliveryNotFoundException(lotId));
        verifyDeliveryOwner(delivery);
        FoodLot lot = delivery.getLot();

        DestinationPoint point = destinationPointRepository.findByIdWithCategories(request.destinationPointId())
                .orElseThrow(() -> new DestinationPointNotFoundException(request.destinationPointId()));

        if (!point.getAcceptedCategories().contains(lot.getCategory())) {
            throw new CategoryNotAcceptedException(point.getId(), lot.getCategory());
        }

        statusChanger.transition(lot, LotStatus.DELIVERED, "Доставлено до пункту призначення");

        delivery.setDestinationPoint(point);
        delivery.setDeliveredAt(Instant.now(clock));
        delivery.setConfirmationCode(generateConfirmationCode());

        deliveryRepository.save(delivery);
        lotRepository.save(lot);

        return DeliveryResponse.from(delivery);
    }

    @Override
    @Transactional(noRollbackFor = {InvalidConfirmationCodeException.class, ConfirmationAttemptsExceededException.class})
    public DeliveryResponse confirm(UUID lotId, ConfirmationRequest request) {
        Delivery delivery = deliveryRepository.findByLotIdWithLot(lotId)
                .orElseThrow(() -> new DeliveryNotFoundException(lotId));
        verifyDeliveryOwner(delivery);
        FoodLot lot = delivery.getLot();

        if (delivery.getFailedConfirmationAttempts() >= MAX_CONFIRMATION_ATTEMPTS) {
            throw new ConfirmationAttemptsExceededException();
        }
        if (!request.confirmationCode().equals(delivery.getConfirmationCode())) {
            delivery.registerFailedConfirmationAttempt();
            deliveryRepository.save(delivery);
            log.warn("Невірний код підтвердження для лота {}", lotId);
            if (delivery.getFailedConfirmationAttempts() >= MAX_CONFIRMATION_ATTEMPTS) {
                throw new ConfirmationAttemptsExceededException();
            }
            throw new InvalidConfirmationCodeException();
        }

        WeightToleranceStrategy strategy = weightToleranceStrategies.get(lot.getCategory());
        if (strategy == null) {
            throw new IllegalStateException("Немає стратегії допуску ваги для категорії " + lot.getCategory());
        }

        LotStatus nextStatus = hasWeightDifference(
                delivery.getPickupWeightKg(),
                request.receivedWeightKg(),
                strategy.tolerancePercent())
                ? LotStatus.DISPUTED
                : LotStatus.CONFIRMED;

        statusChanger.transition(
                lot,
                nextStatus,
                nextStatus == LotStatus.CONFIRMED
                        ? "Отримання підтверджено"
                        : "Виявлено розбіжність у вазі");

        delivery.setConfirmedAt(Instant.now(clock));
        delivery.setReceivedWeightKg(request.receivedWeightKg());

        deliveryRepository.save(delivery);
        lotRepository.save(lot);

        DeliveryResponse response = DeliveryResponse.from(delivery);
        DeliveryOutcome outcome = nextStatus == LotStatus.CONFIRMED
                ? DeliveryOutcome.CONFIRMED
                : DeliveryOutcome.DISPUTED;

        eventPublisher.publishEvent(new DeliveryFinishedEvent(
                lot.getId(),
                lot.getDonorOrgId(),
                delivery.getVolunteerId(),
                outcome,
                delivery.isLatePickup()));

        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public List<StatusHistoryResponse> getHistory(UUID lotId) {
        lotRepository.findById(lotId)
                .orElseThrow(() -> new LotNotFoundException(lotId));

        return historyRecorder.findByLot(lotId).stream()
                .map(this::toHistoryResponse)
                .toList();
    }

    private StatusHistoryResponse toHistoryResponse(LotStatusHistory history) {
        return new StatusHistoryResponse(
                history.getFromStatus(),
                history.getToStatus(),
                history.getChangedAt(),
                history.getComment());
    }

    private boolean hasWeightDifference(BigDecimal pickupWeight,
                                        BigDecimal receivedWeight,
                                        BigDecimal tolerancePercent) {
        BigDecimal differenceTimesHundred = receivedWeight.subtract(pickupWeight).abs().multiply(ONE_HUNDRED);
        BigDecimal allowedDifferenceTimesHundred = pickupWeight.multiply(tolerancePercent);
        return differenceTimesHundred.compareTo(allowedDifferenceTimesHundred) > 0;
    }

    private boolean isCoordinator() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_COORDINATOR".equals(authority.getAuthority()));
    }

    private void verifyDeliveryOwner(Delivery delivery) {
        if (!isCoordinator()
                && !Objects.equals(currentUser.profileId(), delivery.getVolunteerId())) {
            throw new ForbiddenActionException("Доставка належить іншому волонтеру");
        }
    }

    private String generateConfirmationCode() {
        return "%06d".formatted(CODE_RANDOM.nextInt(1_000_000));
    }
}
