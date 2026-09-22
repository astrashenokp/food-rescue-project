package com.example.foodrescue.volunteer;

import com.example.foodrescue.common.FoodCategory;
import com.example.foodrescue.common.FoodLot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReservationAccessStrategiesTest {

    private static final Instant NOW = Instant.parse("2026-09-20T12:00:00Z");

    private FoodLot lot(FoodCategory category, BigDecimal weight, Instant publishedAt) {
        FoodLot lot = new FoodLot();
        lot.setCategory(category);
        lot.setTotalWeightKg(weight);
        lot.setPublishedAt(publishedAt);
        return lot;
    }

    // --- TrustedAccess ---

    @Test
    void trusted_returnsCorrectTier() {
        assertEquals(VolunteerTier.TRUSTED, new TrustedAccess().tier());
    }

    @Test
    void trusted_alwaysAllows() {
        FoodLot bigLotEarlyWindow = lot(FoodCategory.PREPARED_MEAL, BigDecimal.valueOf(25), NOW.minus(5, ChronoUnit.MINUTES));
        assertTrue(new TrustedAccess().canReserve(bigLotEarlyWindow, NOW));
    }

    // --- StandardAccess ---

    @Test
    void standard_returnsCorrectTier() {
        assertEquals(VolunteerTier.STANDARD, new StandardAccess().tier());
    }

    @Test
    void standard_smallLot_allows() {
        FoodLot smallLot = lot(FoodCategory.BAKERY, BigDecimal.valueOf(5), NOW.minus(5, ChronoUnit.MINUTES));
        assertTrue(new StandardAccess().canReserve(smallLot, NOW));
    }

    @Test
    void standard_bigLotAfterEarlyWindow_allows() {
        FoodLot bigLot = lot(FoodCategory.BAKERY, BigDecimal.valueOf(25), NOW.minus(15, ChronoUnit.MINUTES));
        assertTrue(new StandardAccess().canReserve(bigLot, NOW));
    }

    @Test
    void standard_bigLotInEarlyWindow_denies() {
        FoodLot bigLot = lot(FoodCategory.BAKERY, BigDecimal.valueOf(25), NOW.minus(5, ChronoUnit.MINUTES));
        assertFalse(new StandardAccess().canReserve(bigLot, NOW));
    }

    @Test
    void standard_bigLotExactlyAtWindowBorder_allows() {
        FoodLot bigLot = lot(FoodCategory.BAKERY, BigDecimal.valueOf(20), NOW.minus(10, ChronoUnit.MINUTES));
        assertTrue(new StandardAccess().canReserve(bigLot, NOW));
    }

    // --- RestrictedAccess ---

    @Test
    void restricted_returnsCorrectTier() {
        assertEquals(VolunteerTier.RESTRICTED, new RestrictedAccess().tier());
    }

    @Test
    void restricted_bakery_allows() {
        FoodLot bakery = lot(FoodCategory.BAKERY, BigDecimal.valueOf(5), NOW.minus(1, ChronoUnit.HOURS));
        assertTrue(new RestrictedAccess().canReserve(bakery, NOW));
    }

    @Test
    void restricted_grocery_allows() {
        FoodLot grocery = lot(FoodCategory.GROCERY, BigDecimal.valueOf(5), NOW.minus(1, ChronoUnit.HOURS));
        assertTrue(new RestrictedAccess().canReserve(grocery, NOW));
    }

    @Test
    void restricted_preparedMeal_denies() {
        FoodLot meal = lot(FoodCategory.PREPARED_MEAL, BigDecimal.valueOf(5), NOW.minus(1, ChronoUnit.HOURS));
        assertFalse(new RestrictedAccess().canReserve(meal, NOW));
    }

    @Test
    void restricted_vegetables_denies() {
        FoodLot veg = lot(FoodCategory.VEGETABLES, BigDecimal.valueOf(5), NOW.minus(1, ChronoUnit.HOURS));
        assertFalse(new RestrictedAccess().canReserve(veg, NOW));
    }

    @Test
    void restricted_bakeryBigLotInEarlyWindow_denies() {
        FoodLot bigBakery = lot(FoodCategory.BAKERY, BigDecimal.valueOf(25), NOW.minus(5, ChronoUnit.MINUTES));
        assertFalse(new RestrictedAccess().canReserve(bigBakery, NOW));
    }

    @Test
    void restricted_bakeryBigLotAfterEarlyWindow_allows() {
        FoodLot bigBakery = lot(FoodCategory.BAKERY, BigDecimal.valueOf(25), NOW.minus(15, ChronoUnit.MINUTES));
        assertTrue(new RestrictedAccess().canReserve(bigBakery, NOW));
    }
}
