package com.example.foodrescue.lot;

import com.example.foodrescue.common.FoodItem;
import com.example.foodrescue.common.FoodItemRepository;
import com.example.foodrescue.common.FoodItemResponse;
import com.example.foodrescue.common.FoodLot;
import com.example.foodrescue.common.LotNotFoundException;
import com.example.foodrescue.common.LotRepository;
import com.example.foodrescue.common.LotStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class LotItemServiceImpl implements LotItemService {

    private final LotRepository lotRepository;
    private final FoodItemRepository foodItemRepository;

    public LotItemServiceImpl(LotRepository lotRepository, FoodItemRepository foodItemRepository) {
        this.lotRepository = lotRepository;
        this.foodItemRepository = foodItemRepository;
    }

    @Override
    @Transactional
    public FoodItemResponse addItem(UUID lotId, FoodItemRequest request) {
        FoodLot lot = lotRepository.findByIdWithItems(lotId).orElseThrow(() -> new LotNotFoundException(lotId));
        requireDraft(lot);

        FoodItem item = new FoodItem(request.name(), request.quantity(), request.unit(), request.bestBefore());
        lot.addItem(item);
        return FoodItemResponse.from(item);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FoodItemResponse> findItems(UUID lotId) {
        requireLotExists(lotId);
        return foodItemRepository.findByLotId(lotId).stream()
                .map(FoodItemResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public FoodItemResponse getItem(UUID lotId, UUID itemId) {
        requireLotExists(lotId);
        return FoodItemResponse.from(findItem(lotId, itemId));
    }

    @Override
    @Transactional
    public FoodItemResponse updateItem(UUID lotId, UUID itemId, FoodItemRequest request) {
        FoodLot lot = lotRepository.findById(lotId).orElseThrow(() -> new LotNotFoundException(lotId));
        requireDraft(lot);

        FoodItem item = findItem(lotId, itemId);
        item.setName(request.name());
        item.setQuantity(request.quantity());
        item.setUnit(request.unit());
        item.setBestBefore(request.bestBefore());
        return FoodItemResponse.from(foodItemRepository.save(item));
    }

    @Override
    @Transactional
    public void deleteItem(UUID lotId, UUID itemId) {
        FoodLot lot = lotRepository.findByIdWithItems(lotId).orElseThrow(() -> new LotNotFoundException(lotId));
        requireDraft(lot);

        FoodItem item = findItem(lotId, itemId);
        lot.removeItem(item);
    }

    private void requireLotExists(UUID lotId) {
        if (!lotRepository.existsById(lotId)) {
            throw new LotNotFoundException(lotId);
        }
    }

    private void requireDraft(FoodLot lot) {
        if (lot.getStatus() != LotStatus.DRAFT) {
            throw new LotNotDraftException(lot.getId());
        }
    }

    private FoodItem findItem(UUID lotId, UUID itemId) {
        return foodItemRepository.findByIdAndLotId(itemId, lotId)
                .orElseThrow(() -> new FoodItemNotFoundException(lotId, itemId));
    }
}
