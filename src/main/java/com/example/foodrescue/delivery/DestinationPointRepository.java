package com.example.foodrescue.delivery;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DestinationPointRepository extends ListCrudRepository<DestinationPoint, UUID> {

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, UUID id);

    @Query("SELECT DISTINCT p FROM DestinationPoint p LEFT JOIN FETCH p.acceptedCategories ORDER BY p.name")
    List<DestinationPoint> findAllWithCategories();

    @Query("SELECT DISTINCT p FROM DestinationPoint p LEFT JOIN FETCH p.acceptedCategories WHERE p.id = :id")
    Optional<DestinationPoint> findByIdWithCategories(@Param("id") UUID id);
}
