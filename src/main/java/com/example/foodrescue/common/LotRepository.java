package com.example.foodrescue.common;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LotRepository extends JpaRepository<FoodLot, UUID> {

    long countByStatus(LotStatus status);

    List<FoodLot> findByStatusAndPickupToBefore(LotStatus status, Instant time);

    List<FoodLot> findByStatusAndReservedUntilBefore(LotStatus status, Instant time);

    long countByDonorOrgId(UUID donorOrgId);

    long countByDonorOrgIdAndStatus(UUID donorOrgId, LotStatus status);

    boolean existsByReservedByVolunteerIdAndStatusIn(UUID volunteerId, Collection<LotStatus> statuses);

    @Query("SELECT DISTINCT l FROM FoodLot l LEFT JOIN FETCH l.items ORDER BY l.createdAt DESC")
    List<FoodLot> findAllWithItems();

    @Query("SELECT DISTINCT l FROM FoodLot l LEFT JOIN FETCH l.items WHERE l.status = :status ORDER BY l.createdAt DESC")
    List<FoodLot> findAllByStatusWithItems(@Param("status") LotStatus status);

    @Query("SELECT DISTINCT l FROM FoodLot l LEFT JOIN FETCH l.items WHERE l.id = :id")
    Optional<FoodLot> findByIdWithItems(@Param("id") UUID id);
}
