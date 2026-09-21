package com.example.foodrescue.lot;

import java.util.UUID;

public record DonorStats(UUID donorOrgId, int confirmedLots, int disputedLots) {
}
