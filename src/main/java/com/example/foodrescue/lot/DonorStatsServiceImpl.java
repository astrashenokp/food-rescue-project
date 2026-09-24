package com.example.foodrescue.lot;

import com.example.foodrescue.delivery.DeliveryOutcome;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class DonorStatsServiceImpl implements DonorStatsService {

    private final DonorStatsRepository donorStatsRepository;

    public DonorStatsServiceImpl(DonorStatsRepository donorStatsRepository) {
        this.donorStatsRepository = donorStatsRepository;
    }

    @Override
    @Transactional
    public void recordOutcome(UUID donorOrgId, DeliveryOutcome outcome) {
        if (increment(donorOrgId, outcome) == 0) {
            donorStatsRepository.save(new DonorStats(donorOrgId, 0, 0));
            increment(donorOrgId, outcome);
        }
    }

    private int increment(UUID donorOrgId, DeliveryOutcome outcome) {
        return switch (outcome) {
            case CONFIRMED -> donorStatsRepository.incrementConfirmed(donorOrgId);
            case DISPUTED -> donorStatsRepository.incrementDisputed(donorOrgId);
        };
    }
}
