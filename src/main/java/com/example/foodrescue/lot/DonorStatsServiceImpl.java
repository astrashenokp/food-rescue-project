package com.example.foodrescue.lot;

import com.example.foodrescue.delivery.DeliveryOutcome;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class DonorStatsServiceImpl implements DonorStatsService {

    private final DonorStatsRepository donorStatsRepository;

    public DonorStatsServiceImpl(DonorStatsRepository donorStatsRepository) {
        this.donorStatsRepository = donorStatsRepository;
    }

    @Override
    public synchronized void recordOutcome(UUID donorOrgId, DeliveryOutcome outcome) {
        DonorStats current = donorStatsRepository.findByDonorOrgId(donorOrgId)
                .orElseGet(() -> new DonorStats(donorOrgId, 0, 0));

        DonorStats updated = switch (outcome) {
            case CONFIRMED -> new DonorStats(donorOrgId, current.confirmedLots() + 1, current.disputedLots());
            case DISPUTED -> new DonorStats(donorOrgId, current.confirmedLots(), current.disputedLots() + 1);
        };

        donorStatsRepository.save(updated);
    }
}
