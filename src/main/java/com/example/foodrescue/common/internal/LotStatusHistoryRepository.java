package com.example.foodrescue.common.internal;

import com.example.foodrescue.common.LotStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LotStatusHistoryRepository extends JpaRepository<LotStatusHistory, Long> {

    List<LotStatusHistory> findByLotIdOrderByIdAsc(UUID lotId);
}
