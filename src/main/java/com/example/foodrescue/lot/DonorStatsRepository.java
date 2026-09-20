package com.example.foodrescue.lot;

import java.util.Optional;
import java.util.UUID;

public interface DonorStatsRepository {

    DonorStats save(DonorStats stats);

    Optional<DonorStats> findByDonorOrgId(UUID donorOrgId);
}
