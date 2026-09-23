package com.example.foodrescue.delivery;

import com.example.foodrescue.common.FoodCategory;
import com.example.foodrescue.common.FoodLot;
import com.example.foodrescue.common.LotNotFoundException;
import com.example.foodrescue.common.LotRepository;
import com.example.foodrescue.common.LotStatus;
import com.example.foodrescue.common.LotStatusChanger;
import com.example.foodrescue.common.LotStatusHistory;
import com.example.foodrescue.common.StatusHistoryRecorder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class DeliveryServiceImpl implements DeliveryService {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final LotRepository lotRepository;
    private final LotStatusChanger statusChanger;
    private final StatusHistoryRecorder historyRecorder;
    private final DestinationPointRepository destinationPointRepository;
    private final DeliveryRepository deliveryRepository;
    private final Map<FoodCategory, WeightToleranceStrategy> weightToleranceStrategies = new EnumMap<>(FoodCategory.class);
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public DeliveryServiceImpl(
            LotRepository lotRepository,
            LotStatusChanger statusChanger,
            StatusHistoryRecorder historyRecorder,
            DestinationPointRepository destinationPointRepository,
            DeliveryRepository deliveryRepository,
            List<WeightToleranceStrategy> weightToleranceStrategies,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.lotRepository = lotRepository;
        this.statusChanger = statusChanger;
        this.historyRecorder = historyRecorder;
        this.destinationPointRepository = destinationPointRepository;
        this.deliveryRepository = deliveryRepository;
        weightToleranceStrategies.forEach(strategy -> this.weightToleranceStrategies.put(strategy.category(), strategy));
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    public DestinationPoint createDestinationPoint(DestinationPointRequest request) {
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

        return destinationPointRepository.save(point);
    }

    @Override
    public DestinationPoint getDestinationPoint(UUID id) {
        return destinationPointRepository.findById(id)
                .orElseThrow(() -> new DestinationPointNotFoundException(id));
    }

    @Override
    public DeliveryResponse pickup(UUID lotId, PickupRequest request) {
        FoodLot lot = lotRepository.findById(lotId)
                .orElseThrow(() -> new LotNotFoundException(lotId));
        Instant now = Instant.now(clock);
        boolean latePickup = lot.getReservedUntil() != null && now.isAfter(lot.getReservedUntil());

        statusChanger.transition(lot, LotStatus.PICKED_UP, "Лот передано волонтеру");

        Delivery delivery = new Delivery(
                lotId,
                lot.getReservedByVolunteerId(),
                null,
                now,
                request.actualWeightKg(),
                latePickup,
                null,
                null,
                null,
                null);

        deliveryRepository.save(delivery);
        lotRepository.save(lot);

        return toResponse(delivery, lot.getStatus());
    }

    @Override
    public DeliveryResponse deliver(UUID lotId, DeliveryRequest request) {
        FoodLot lot = lotRepository.findById(lotId)
                .orElseThrow(() -> new LotNotFoundException(lotId));
        DestinationPoint point = destinationPointRepository.findById(request.destinationPointId())
                .orElseThrow(() -> new DestinationPointNotFoundException(request.destinationPointId()));

        if (!point.acceptedCategories().contains(lot.getCategory())) {
            throw new CategoryNotAcceptedException(point.id(), lot.getCategory());
        }

        Delivery current = deliveryRepository.findByLotId(lotId)
                .orElseThrow(() -> new DeliveryNotFoundException(lotId));

        statusChanger.transition(lot, LotStatus.DELIVERED, "Доставлено до пункту призначення");

        Delivery updated = new Delivery(
                current.lotId(),
                current.volunteerId(),
                point.id(),
                current.pickedUpAt(),
                current.pickupWeightKg(),
                current.latePickup(),
                Instant.now(clock),
                generateConfirmationCode(),
                null,
                null);

        deliveryRepository.save(updated);
        lotRepository.save(lot);

        return toResponse(updated, lot.getStatus());
    }

    @Override
    @Transactional
    public DeliveryResponse confirm(UUID lotId, ConfirmationRequest request) {
        FoodLot lot = lotRepository.findById(lotId)
                .orElseThrow(() -> new LotNotFoundException(lotId));
        Delivery current = deliveryRepository.findByLotId(lotId)
                .orElseThrow(() -> new DeliveryNotFoundException(lotId));

        if (!request.confirmationCode().equals(current.confirmationCode())) {
            throw new InvalidConfirmationCodeException();
        }

        WeightToleranceStrategy strategy = weightToleranceStrategies.get(lot.getCategory());
        if (strategy == null) {
            throw new IllegalStateException("Немає стратегії допуску ваги для категорії " + lot.getCategory());
        }

        LotStatus nextStatus = hasWeightDifference(
                current.pickupWeightKg(),
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

        Delivery updated = new Delivery(
                current.lotId(),
                current.volunteerId(),
                current.destinationPointId(),
                current.pickedUpAt(),
                current.pickupWeightKg(),
                current.latePickup(),
                current.deliveredAt(),
                current.confirmationCode(),
                Instant.now(clock),
                request.receivedWeightKg());

        deliveryRepository.save(updated);
        lotRepository.save(lot);

        DeliveryResponse response = toResponse(updated, lot.getStatus());
        DeliveryOutcome outcome = nextStatus == LotStatus.CONFIRMED
                ? DeliveryOutcome.CONFIRMED
                : DeliveryOutcome.DISPUTED;

        eventPublisher.publishEvent(new DeliveryFinishedEvent(
                lot.getId(),
                lot.getDonorOrgId(),
                updated.volunteerId(),
                outcome,
                updated.latePickup()));

        return response;
    }

    @Override
    public List<StatusHistoryResponse> getHistory(UUID lotId) {
        lotRepository.findById(lotId)
                .orElseThrow(() -> new LotNotFoundException(lotId));

        return historyRecorder.findByLot(lotId).stream()
                .map(this::toHistoryResponse)
                .toList();
    }

    private DeliveryResponse toResponse(Delivery delivery, LotStatus status) {
        return new DeliveryResponse(
                delivery.lotId(),
                delivery.volunteerId(),
                delivery.destinationPointId(),
                status,
                delivery.pickedUpAt(),
                delivery.pickupWeightKg(),
                delivery.deliveredAt(),
                delivery.confirmationCode(),
                delivery.confirmedAt(),
                delivery.receivedWeightKg());
    }

    private StatusHistoryResponse toHistoryResponse(LotStatusHistory history) {
        return new StatusHistoryResponse(
                history.fromStatus(),
                history.toStatus(),
                history.changedAt(),
                history.comment());
    }

    private boolean hasWeightDifference(BigDecimal pickupWeight,
                                        BigDecimal receivedWeight,
                                        BigDecimal tolerancePercent) {
        BigDecimal differenceTimesHundred = receivedWeight.subtract(pickupWeight).abs().multiply(ONE_HUNDRED);
        BigDecimal allowedDifferenceTimesHundred = pickupWeight.multiply(tolerancePercent);
        return differenceTimesHundred.compareTo(allowedDifferenceTimesHundred) > 0;
    }

    private String generateConfirmationCode() {
        return "%06d".formatted(ThreadLocalRandom.current().nextInt(1_000_000));
    }
}
