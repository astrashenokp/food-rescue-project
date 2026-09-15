package com.example.foodrescue.lot;

import com.example.foodrescue.common.lot.FoodCategory;
import com.example.foodrescue.common.lot.FoodItem;
import com.example.foodrescue.common.lot.FoodLot;
import com.example.foodrescue.common.lot.LotStatus;
import com.example.foodrescue.common.lot.LotStatusChanger;
import com.example.foodrescue.common.lot.LotStore;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class LotService {

    private static final int MIN_DONOR_LOTS_FOR_MODERATION_CHECK = 5;
    private static final int CANCELLATION_THRESHOLD_PERCENT = 20;

    private final LotStore lotStore;
    private final LotStatusChanger statusChanger;

    public LotService(LotStore lotStore, LotStatusChanger statusChanger) {
        this.lotStore = lotStore;
        this.statusChanger = statusChanger;
    }

    public FoodLot create(LotRequest request) {
        FoodLot lot = new FoodLot();
        lot.setId(UUID.randomUUID());
        applyRequest(lot, request);
        lot.setCreatedAt(Instant.now());
        lot.setStatus(LotStatus.DRAFT);
        return lotStore.save(lot);
    }

    public List<FoodLot> findAll(LotStatus status, FoodCategory category, UUID donorOrgId) {
        return lotStore.findAll().stream()
                .filter(lot -> status == null || lot.getStatus() == status)
                .filter(lot -> category == null || lot.getCategory() == category)
                .filter(lot -> donorOrgId == null || donorOrgId.equals(lot.getDonorOrgId()))
                .toList();
    }

    public FoodLot getById(UUID id) {
        return lotStore.getById(id);
    }

    public FoodLot update(UUID id, LotRequest request) {
        FoodLot lot = lotStore.getById(id);
        if (lot.getStatus() != LotStatus.DRAFT) {
            throw new LotNotDraftException(id);
        }
        applyRequest(lot, request);
        return lotStore.save(lot);
    }

    public FoodLot publish(UUID id) {
        FoodLot lot = lotStore.getById(id);
        LotStatus target = shouldSendToModeration(lot.getDonorOrgId())
                ? LotStatus.PENDING_MODERATION
                : LotStatus.PUBLISHED;

        statusChanger.transition(lot, target, "Публікація лоту");

        if (target == LotStatus.PUBLISHED) {
            lot.setPublishedAt(Instant.now());
        }
        return lotStore.save(lot);
    }

    public FoodLot cancel(UUID id) {
        FoodLot lot = lotStore.getById(id);
        statusChanger.transition(lot, LotStatus.CANCELLED, "Скасовано донором");
        return lotStore.save(lot);
    }

    public FoodLot approve(UUID id, ApprovalRequest request) {
        FoodLot lot = lotStore.getById(id);
        LotStatus target = Boolean.TRUE.equals(request.approved()) ? LotStatus.PUBLISHED : LotStatus.DRAFT;
        String comment = request.comment() != null ? request.comment() : "Рішення модератора";

        statusChanger.transition(lot, target, comment);

        if (target == LotStatus.PUBLISHED) {
            lot.setPublishedAt(Instant.now());
        }
        return lotStore.save(lot);
    }

    private void applyRequest(FoodLot lot, LotRequest request) {
        lot.setDonorOrgId(request.donorOrgId());
        lot.setTitle(request.title());
        lot.setCategory(request.category());
        lot.setItems(request.items().stream().map(this::toFoodItem).toList());
        lot.setTotalWeightKg(request.totalWeightKg());
        lot.setStorageCondition(request.storageCondition());
        lot.setPickupAddress(request.pickupAddress());
        lot.setPickupFrom(request.pickupFrom());
        lot.setPickupTo(request.pickupTo());
    }

    private FoodItem toFoodItem(FoodItemRequest request) {
        return new FoodItem(request.name(), request.quantity(), request.unit(), request.bestBefore());
    }

    private boolean shouldSendToModeration(UUID donorOrgId) {
        List<FoodLot> donorLots = lotStore.findAll().stream()
                .filter(lot -> donorOrgId.equals(lot.getDonorOrgId()))
                .toList();

        if (donorLots.size() < MIN_DONOR_LOTS_FOR_MODERATION_CHECK) {
            return false;
        }

        long cancelledCount = donorLots.stream()
                .filter(lot -> lot.getStatus() == LotStatus.CANCELLED)
                .count();

        return cancelledCount * 100 > (long) donorLots.size() * CANCELLATION_THRESHOLD_PERCENT;
    }
}
