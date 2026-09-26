package com.example.foodrescue.delivery;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeliveryRepository extends ListCrudRepository<Delivery, UUID> {

    boolean existsByDestinationPointId(UUID destinationPointId);

    @Query("SELECT d FROM Delivery d JOIN FETCH d.lot WHERE d.lot.id = :lotId")
    Optional<Delivery> findByLotIdWithLot(@Param("lotId") UUID lotId);

    @Query("SELECT d FROM Delivery d JOIN FETCH d.lot LEFT JOIN FETCH d.destinationPoint ORDER BY d.id")
    List<Delivery> findAllWithLotAndPoint();

    @Query("SELECT d FROM Delivery d JOIN FETCH d.lot LEFT JOIN FETCH d.destinationPoint WHERE d.id = :id")
    Optional<Delivery> findByIdWithLotAndPoint(@Param("id") UUID id);
}
