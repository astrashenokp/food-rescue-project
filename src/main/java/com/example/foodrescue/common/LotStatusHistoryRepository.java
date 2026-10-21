package com.example.foodrescue.common;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LotStatusHistoryRepository extends JpaRepository<LotStatusHistory, Long> {

    List<LotStatusHistory> findByLotIdOrderByIdAsc(UUID lotId);
}
