package com.example.foodrescue.lot;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ApprovalRequest(
        @NotNull Boolean approved,
        @Size(max = 300) String comment) {
}
