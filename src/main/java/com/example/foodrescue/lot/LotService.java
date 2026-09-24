package com.example.foodrescue.lot;

import com.example.foodrescue.common.FoodCategory;
import com.example.foodrescue.common.LotResponse;
import com.example.foodrescue.common.LotStatus;

import java.util.List;
import java.util.UUID;

public interface LotService {

    LotResponse create(LotRequest request);

    List<LotResponse> findAll(LotStatus status, FoodCategory category, UUID donorOrgId);

    LotResponse getById(UUID id);

    LotResponse update(UUID id, LotRequest request);

    void delete(UUID id);

    LotResponse publish(UUID id);

    LotResponse cancel(UUID id);

    LotResponse approve(UUID id, ApprovalRequest request);
}
