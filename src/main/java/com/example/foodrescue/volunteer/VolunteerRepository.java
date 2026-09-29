package com.example.foodrescue.volunteer;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VolunteerRepository extends ListCrudRepository<VolunteerProfile, UUID> {

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, UUID id);

    @Query("SELECT DISTINCT v FROM VolunteerProfile v LEFT JOIN FETCH v.preferredPoints ORDER BY v.fullName")
    List<VolunteerProfile> findAllWithPoints();

    @Query("SELECT DISTINCT v FROM VolunteerProfile v LEFT JOIN FETCH v.preferredPoints WHERE v.id = :id")
    Optional<VolunteerProfile> findByIdWithPoints(@Param("id") UUID id);

    @Modifying
    @Query("UPDATE VolunteerProfile v SET v.completedDeliveries = v.completedDeliveries + 1 WHERE v.id = :id")
    int incrementCompleted(@Param("id") UUID id);

    @Modifying
    @Query("UPDATE VolunteerProfile v SET v.noShows = v.noShows + 1 WHERE v.id = :id")
    int incrementNoShows(@Param("id") UUID id);

    @Modifying
    @Query("UPDATE VolunteerProfile v SET v.latePickups = v.latePickups + 1 WHERE v.id = :id")
    int incrementLatePickups(@Param("id") UUID id);
}
