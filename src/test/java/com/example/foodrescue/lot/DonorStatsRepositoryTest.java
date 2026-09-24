package com.example.foodrescue.lot;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class DonorStatsRepositoryTest {

    private static final UUID DONOR = UUID.fromString("00000000-0000-0000-0000-00000000d001");

    @Autowired
    private DonorStatsRepository donorStatsRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void incrementConfirmed_missingDonor_updatesNothing() {
        assertEquals(0, donorStatsRepository.incrementConfirmed(DONOR));
        assertEquals(0, donorStatsRepository.incrementDisputed(DONOR));
        assertTrue(donorStatsRepository.findById(DONOR).isEmpty());
    }

    @Test
    void increments_updateOnlyTheirOwnCounterAtomically() {
        donorStatsRepository.save(new DonorStats(DONOR, 0, 0));
        entityManager.flush();

        assertEquals(1, donorStatsRepository.incrementConfirmed(DONOR));
        assertEquals(1, donorStatsRepository.incrementConfirmed(DONOR));
        assertEquals(1, donorStatsRepository.incrementDisputed(DONOR));
        entityManager.clear();

        DonorStats stats = donorStatsRepository.findById(DONOR).orElseThrow();
        assertEquals(2, stats.getConfirmedLots());
        assertEquals(1, stats.getDisputedLots());
    }

    @Test
    void increments_doNotTouchOtherDonors() {
        UUID other = UUID.randomUUID();
        donorStatsRepository.save(new DonorStats(DONOR, 0, 0));
        donorStatsRepository.save(new DonorStats(other, 5, 5));
        entityManager.flush();

        donorStatsRepository.incrementConfirmed(DONOR);
        entityManager.clear();

        assertEquals(5, donorStatsRepository.findById(other).orElseThrow().getConfirmedLots());
        assertEquals(1, donorStatsRepository.findById(DONOR).orElseThrow().getConfirmedLots());
    }
}
