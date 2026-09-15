package com.example.foodrescue.delivery;

import com.example.foodrescue.common.error.BusinessRuleException;
import com.example.foodrescue.common.history.LotStatusHistory;
import com.example.foodrescue.common.history.StatusHistoryRecorder;
import com.example.foodrescue.common.lot.FoodLot;
import com.example.foodrescue.common.lot.LotStatus;
import com.example.foodrescue.common.lot.LotStatusChanger;
import com.example.foodrescue.common.lot.LotStore;
import com.example.foodrescue.volunteer.VolunteerStatsRecorder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class DeliveryService {

    private static final BigDecimal MAX_WEIGHT_DIFFERENCE = new BigDecimal("0.10");

    private final LotStore lotStore;
    private final LotStatusChanger statusChanger;
    private final StatusHistoryRecorder historyRecorder;
    private final DestinationPointStore destinationPointStore;
    private final DeliveryStore deliveryStore;
    private final VolunteerStatsRecorder volunteerStatsRecorder;

    public DeliveryService(
            LotStore lotStore,
            LotStatusChanger statusChanger,
            StatusHistoryRecorder historyRecorder,
            DestinationPointStore destinationPointStore,
            DeliveryStore deliveryStore,
            VolunteerStatsRecorder volunteerStatsRecorder) {
        this.lotStore = lotStore;
        this.statusChanger = statusChanger;
        this.historyRecorder = historyRecorder;
        this.destinationPointStore = destinationPointStore;
        this.deliveryStore = deliveryStore;
        this.volunteerStatsRecorder = volunteerStatsRecorder;
    }

    public DestinationPoint createDestinationPoint(DestinationPointRequest request) {
        DestinationPoint point = new DestinationPoint(
                UUID.randomUUID(),
                request.organizationId(),
                request.name(),
                request.address(),
                request.workingHours(),
                request.acceptedCategories());
        return destinationPointStore.save(point);
    }

    public DestinationPoint getDestinationPoint(UUID id) {
        return destinationPointStore.getById(id);
    }

    public DeliveryResponse pickup(UUID lotId, PickupRequest request) {
        FoodLot lot = lotStore.getById(lotId);
        Instant now = Instant.now();
        boolean late = lot.getReservedUntil() != null && now.isAfter(lot.getReservedUntil());

        statusChanger.transition(lot, LotStatus.PICKED_UP, "Лот передано волонтеру");

        Delivery delivery = new Delivery(
                lotId,
                lot.getReservedByVolunteerId(),
                null,
                now,
                request.actualWeightKg(),
                null,
                null,
                null,
                null);

        deliveryStore.save(delivery);
        lotStore.save(lot);
        volunteerStatsRecorder.recordPickup(delivery.volunteerId(), late);

        return toResponse(delivery, lot.getStatus());
    }

    public DeliveryResponse deliver(UUID lotId, DeliveryRequest request) {
        FoodLot lot = lotStore.getById(lotId);
        DestinationPoint point = destinationPointStore.getById(request.destinationPointId());

        if (!point.acceptedCategories().contains(lot.getCategory())) {
            throw new BusinessRuleException("Пункт призначення не приймає категорію лоту");
        }

        Delivery current = deliveryStore.getByLotId(lotId);
        statusChanger.transition(lot, LotStatus.DELIVERED, "Доставлено до пункту призначення");

        Delivery updated = new Delivery(
                current.lotId(),
                current.volunteerId(),
                point.id(),
                current.pickedUpAt(),
                current.pickupWeightKg(),
                Instant.now(),
                generateConfirmationCode(),
                null,
                null);

        deliveryStore.save(updated);
        lotStore.save(lot);
        return toResponse(updated, lot.getStatus());
    }

    public DeliveryResponse confirm(UUID lotId, ConfirmationRequest request) {
        FoodLot lot = lotStore.getById(lotId);
        Delivery current = deliveryStore.getByLotId(lotId);

        if (!request.confirmationCode().equals(current.confirmationCode())) throw new BusinessRuleException("Неправильний код підтвердження");

        LotStatus nextStatus = hasWeightDifference(current.pickupWeightKg(), request.receivedWeightKg())
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
                current.deliveredAt(),
                current.confirmationCode(),
                Instant.now(),
                request.receivedWeightKg());

        deliveryStore.save(updated);
        lotStore.save(lot);

        if (nextStatus == LotStatus.DISPUTED) {
            volunteerStatsRecorder.recordStrike(updated.volunteerId());
        } else {
            volunteerStatsRecorder.recordCompletedDelivery(updated.volunteerId());
        }

        return toResponse(updated, lot.getStatus());
    }

    public List<StatusHistoryResponse> getHistory(UUID lotId) {
        lotStore.getById(lotId);
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

    private boolean hasWeightDifference(BigDecimal pickupWeight, BigDecimal receivedWeight) {
        BigDecimal difference = receivedWeight.subtract(pickupWeight).abs();
        BigDecimal limit = pickupWeight.multiply(MAX_WEIGHT_DIFFERENCE);
        return difference.compareTo(limit) > 0;
    }

    private String generateConfirmationCode() {
        return "%06d".formatted(ThreadLocalRandom.current().nextInt(1_000_000));
    }
}
