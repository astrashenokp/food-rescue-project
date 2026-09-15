package com.example.foodrescue.lot;

import com.example.foodrescue.common.lot.FoodCategory;
import com.example.foodrescue.common.lot.StorageCondition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@ValidPickupWindow
public record LotRequest(
        @NotNull UUID donorOrgId,
        @NotBlank @Size(min = 3, max = 100) String title,
        @NotNull FoodCategory category,
        @NotEmpty List<@Valid FoodItemRequest> items,
        @NotNull @DecimalMin("0.1") BigDecimal totalWeightKg,
        @NotNull StorageCondition storageCondition,
        @NotBlank @Size(max = 200) String pickupAddress,
        @NotNull @Future Instant pickupFrom,
        @NotNull @Future Instant pickupTo) {
}
