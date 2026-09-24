package com.example.foodrescue.lot;

import com.example.foodrescue.common.FoodCategory;
import com.example.foodrescue.common.FoodItem;
import com.example.foodrescue.common.FoodLot;
import com.example.foodrescue.common.LotNotFoundException;
import com.example.foodrescue.common.LotRepository;
import com.example.foodrescue.common.LotStatus;
import com.example.foodrescue.common.LotStatusChanger;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class LotServiceImpl implements LotService {

    private static final int MIN_DONOR_LOTS_FOR_MODERATION_CHECK = 5;
    private static final int CANCELLATION_THRESHOLD_PERCENT = 20;

    private final LotRepository lotRepository;
    private final LotStatusChanger statusChanger;
    private final Map<FoodCategory, PickupWindowStrategy> pickupWindowStrategies = new EnumMap<>(FoodCategory.class);
    private final Clock clock;

    public LotServiceImpl(LotRepository lotRepository,
                          LotStatusChanger statusChanger,
                          List<PickupWindowStrategy> pickupWindowStrategies,
                          Clock clock) {
        this.lotRepository = lotRepository;
        this.statusChanger = statusChanger;
        pickupWindowStrategies.forEach(strategy -> this.pickupWindowStrategies.put(strategy.category(), strategy));
        this.clock = clock;
    }

    @Override
    public FoodLot create(LotRequest request) {
        Instant now = Instant.now(clock);
        validatePickupWindow(request, now);

        FoodLot lot = new FoodLot(UUID.randomUUID());
        applyRequest(lot, request);
        lot.setCreatedAt(now);
        lot.setStatus(LotStatus.DRAFT);
        return lotRepository.save(lot);
    }

    @Override
    public List<FoodLot> findAll(LotStatus status, FoodCategory category, UUID donorOrgId) {
        return lotRepository.findAll().stream()
                .filter(lot -> status == null || lot.getStatus() == status)
                .filter(lot -> category == null || lot.getCategory() == category)
                .filter(lot -> donorOrgId == null || donorOrgId.equals(lot.getDonorOrgId()))
                .toList();
    }

    @Override
    public FoodLot getById(UUID id) {
        return lotRepository.findById(id).orElseThrow(() -> new LotNotFoundException(id));
    }

    @Override
    public FoodLot update(UUID id, LotRequest request) {
        FoodLot lot = lotRepository.findById(id).orElseThrow(() -> new LotNotFoundException(id));
        if (lot.getStatus() != LotStatus.DRAFT) {
            throw new LotNotDraftException(id);
        }
        validatePickupWindow(request, Instant.now(clock));
        applyRequest(lot, request);
        return lotRepository.save(lot);
    }

    @Override
    public FoodLot publish(UUID id) {
        FoodLot lot = lotRepository.findById(id).orElseThrow(() -> new LotNotFoundException(id));
        LotStatus target = shouldSendToModeration(lot.getDonorOrgId())
                ? LotStatus.PENDING_MODERATION
                : LotStatus.PUBLISHED;

        statusChanger.transition(lot, target, "Публікація лоту");

        if (target == LotStatus.PUBLISHED) {
            lot.setPublishedAt(Instant.now(clock));
        }
        return lotRepository.save(lot);
    }

    @Override
    public FoodLot cancel(UUID id) {
        FoodLot lot = lotRepository.findById(id).orElseThrow(() -> new LotNotFoundException(id));
        statusChanger.transition(lot, LotStatus.CANCELLED, "Скасовано донором");
        return lotRepository.save(lot);
    }

    @Override
    public FoodLot approve(UUID id, ApprovalRequest request) {
        FoodLot lot = lotRepository.findById(id).orElseThrow(() -> new LotNotFoundException(id));
        LotStatus target = Boolean.TRUE.equals(request.approved()) ? LotStatus.PUBLISHED : LotStatus.DRAFT;
        String comment = request.comment() != null ? request.comment() : "Рішення модератора";

        statusChanger.transition(lot, target, comment);

        if (target == LotStatus.PUBLISHED) {
            lot.setPublishedAt(Instant.now(clock));
        }
        return lotRepository.save(lot);
    }

    private void validatePickupWindow(LotRequest request, Instant now) {
        Instant from = request.pickupFrom();
        Instant to = request.pickupTo();

        if (!to.isAfter(from)) {
            throw new InvalidPickupWindowException("pickupTo має бути пізніше за pickupFrom");
        }
        if (!from.isAfter(now)) {
            throw new InvalidPickupWindowException("pickupFrom має бути в майбутньому");
        }

        PickupWindowStrategy strategy = pickupWindowStrategies.get(request.category());
        if (strategy == null) {
            throw new IllegalStateException("Немає стратегії вікна самовивозу для категорії " + request.category());
        }
        if (Duration.between(from, to).compareTo(Duration.ofMinutes(strategy.minWindowMinutes())) < 0) {
            throw new InvalidPickupWindowException("Вікно самовивозу для категорії " + request.category()
                    + " має бути щонайменше " + strategy.minWindowMinutes() + " хв");
        }
    }

    private void applyRequest(FoodLot lot, LotRequest request) {
        lot.setDonorOrgId(request.donorOrgId());
        lot.setTitle(request.title());
        lot.setCategory(request.category());
        lot.replaceItems(request.items().stream().map(this::toFoodItem).toList());
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
        List<FoodLot> donorLots = lotRepository.findAll().stream()
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
