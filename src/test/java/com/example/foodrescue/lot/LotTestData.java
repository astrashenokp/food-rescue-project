package com.example.foodrescue.lot;

import com.example.foodrescue.common.FoodCategory;
import com.example.foodrescue.common.FoodItem;
import com.example.foodrescue.common.FoodLot;
import com.example.foodrescue.common.ItemUnit;
import com.example.foodrescue.common.LotStatus;
import com.example.foodrescue.common.StorageCondition;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

final class LotTestData {

    static final Instant BASE_TIME = Instant.parse("2026-09-20T12:00:00Z");

    private LotTestData() {
    }

    static FoodLot lot(UUID donorOrgId, LotStatus status, Instant createdAt, int itemsCount) {
        FoodLot lot = new FoodLot(UUID.randomUUID());
        lot.setDonorOrgId(donorOrgId);
        lot.setTitle("Тестовий лот");
        lot.setCategory(FoodCategory.BAKERY);
        lot.setTotalWeightKg(BigDecimal.valueOf(5));
        lot.setStorageCondition(StorageCondition.ROOM);
        lot.setPickupAddress("вул. Тестова, 1");
        lot.setPickupFrom(createdAt.plus(2, ChronoUnit.HOURS));
        lot.setPickupTo(createdAt.plus(4, ChronoUnit.HOURS));
        lot.setCreatedAt(createdAt);
        lot.setStatus(status);
        for (int i = 1; i <= itemsCount; i++) {
            lot.addItem(new FoodItem("Позиція " + i, BigDecimal.valueOf(i), ItemUnit.KG, null));
        }
        return lot;
    }
}
