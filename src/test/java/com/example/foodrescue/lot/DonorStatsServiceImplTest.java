package com.example.foodrescue.lot;

import com.example.foodrescue.delivery.DeliveryOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class DonorStatsServiceImplTest {

    private static final UUID DONOR = UUID.fromString("00000000-0000-0000-0000-00000000d001");

    @Mock
    private DonorStatsRepository donorStatsRepository;

    private DonorStatsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DonorStatsServiceImpl(donorStatsRepository);
    }

    @Test
    void recordOutcome_confirmedForNewDonor_createsStatsWithOneConfirmed() {
        given(donorStatsRepository.findByDonorOrgId(DONOR)).willReturn(Optional.empty());

        service.recordOutcome(DONOR, DeliveryOutcome.CONFIRMED);

        verify(donorStatsRepository).findByDonorOrgId(DONOR);
        verify(donorStatsRepository).save(new DonorStats(DONOR, 1, 0));
        verifyNoMoreInteractions(donorStatsRepository);
    }

    @Test
    void recordOutcome_disputedForNewDonor_createsStatsWithOneDisputed() {
        given(donorStatsRepository.findByDonorOrgId(DONOR)).willReturn(Optional.empty());

        service.recordOutcome(DONOR, DeliveryOutcome.DISPUTED);

        verify(donorStatsRepository).findByDonorOrgId(DONOR);
        verify(donorStatsRepository).save(new DonorStats(DONOR, 0, 1));
        verifyNoMoreInteractions(donorStatsRepository);
    }

    @Test
    void recordOutcome_confirmedForExistingDonor_incrementsOnlyConfirmed() {
        given(donorStatsRepository.findByDonorOrgId(DONOR)).willReturn(Optional.of(new DonorStats(DONOR, 2, 1)));

        service.recordOutcome(DONOR, DeliveryOutcome.CONFIRMED);

        verify(donorStatsRepository).findByDonorOrgId(DONOR);
        verify(donorStatsRepository).save(new DonorStats(DONOR, 3, 1));
        verifyNoMoreInteractions(donorStatsRepository);
    }

    @Test
    void recordOutcome_disputedForExistingDonor_incrementsOnlyDisputed() {
        given(donorStatsRepository.findByDonorOrgId(DONOR)).willReturn(Optional.of(new DonorStats(DONOR, 2, 1)));

        service.recordOutcome(DONOR, DeliveryOutcome.DISPUTED);

        verify(donorStatsRepository).findByDonorOrgId(DONOR);
        verify(donorStatsRepository).save(new DonorStats(DONOR, 2, 2));
        verifyNoMoreInteractions(donorStatsRepository);
    }
}
