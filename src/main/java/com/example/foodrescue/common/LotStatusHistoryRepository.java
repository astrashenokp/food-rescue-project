package com.example.foodrescue.common;

import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.UUID;

public interface LotStatusHistoryRepository extends ListCrudRepository<LotStatusHistory, Long> {

    List<LotStatusHistory> findByLotIdOrderByIdAsc(UUID lotId);
}
