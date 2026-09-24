package com.example.foodrescue.lot;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface DonorStatsRepository extends ListCrudRepository<DonorStats, UUID> {

    @Modifying
    @Query("UPDATE DonorStats s SET s.confirmedLots = s.confirmedLots + 1 WHERE s.donorOrgId = :id")
    int incrementConfirmed(@Param("id") UUID donorOrgId);

    @Modifying
    @Query("UPDATE DonorStats s SET s.disputedLots = s.disputedLots + 1 WHERE s.donorOrgId = :id")
    int incrementDisputed(@Param("id") UUID donorOrgId);
}
