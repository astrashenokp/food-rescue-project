package com.example.foodrescue.lot;

import com.example.foodrescue.delivery.DeliveryOutcome;

import java.util.UUID;

public interface DonorStatsService {

    void recordOutcome(UUID donorOrgId, DeliveryOutcome outcome);
}
