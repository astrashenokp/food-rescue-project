package com.example.foodrescue.lot;

import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryDonorStatsRepository implements DonorStatsRepository {

    private final Map<UUID, DonorStats> stats = new ConcurrentHashMap<>();

    @Override
    public DonorStats save(DonorStats donorStats) {
        stats.put(donorStats.donorOrgId(), donorStats);
        return donorStats;
    }

    @Override
    public Optional<DonorStats> findByDonorOrgId(UUID donorOrgId) {
        return Optional.ofNullable(stats.get(donorOrgId));
    }
}
