package com.example.foodrescue.lot;

import com.example.foodrescue.common.FoodCategory;
import com.example.foodrescue.common.FoodLot;
import com.example.foodrescue.common.LotStatus;

import java.util.List;
import java.util.UUID;

public interface LotService {

    FoodLot create(LotRequest request);

    List<FoodLot> findAll(LotStatus status, FoodCategory category, UUID donorOrgId);

    FoodLot getById(UUID id);

    FoodLot update(UUID id, LotRequest request);

    FoodLot publish(UUID id);

    FoodLot cancel(UUID id);

    FoodLot approve(UUID id, ApprovalRequest request);
}
