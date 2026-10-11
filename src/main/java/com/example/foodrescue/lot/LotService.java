package com.example.foodrescue.lot;

import com.example.foodrescue.common.FoodCategory;
import com.example.foodrescue.common.LotResponse;
import com.example.foodrescue.common.LotStatus;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;
import java.util.UUID;

public interface LotService {

    @PreAuthorize("hasRole('DONOR')")
    LotResponse create(LotRequest request);

    List<LotResponse> findAll(LotStatus status, FoodCategory category, UUID donorOrgId);

    LotResponse getById(UUID id);

    @PreAuthorize("hasRole('DONOR')")
    LotResponse update(UUID id, LotRequest request);

    @PreAuthorize("hasRole('DONOR')")
    void delete(UUID id);

    @PreAuthorize("hasRole('DONOR')")
    LotResponse publish(UUID id);

    @PreAuthorize("hasRole('DONOR')")
    LotResponse cancel(UUID id);

    @PreAuthorize("hasRole('ADMIN')")
    LotResponse approve(UUID id, ApprovalRequest request);
}
