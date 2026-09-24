package com.example.foodrescue.lot;

import com.example.foodrescue.delivery.DeliveryOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
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
    void recordOutcome_confirmedForExistingDonor_incrementsOnlyConfirmed() {
        given(donorStatsRepository.incrementConfirmed(DONOR)).willReturn(1);

        service.recordOutcome(DONOR, DeliveryOutcome.CONFIRMED);

        verify(donorStatsRepository).incrementConfirmed(DONOR);
        verifyNoMoreInteractions(donorStatsRepository);
    }

    @Test
    void recordOutcome_disputedForExistingDonor_incrementsOnlyDisputed() {
        given(donorStatsRepository.incrementDisputed(DONOR)).willReturn(1);

        service.recordOutcome(DONOR, DeliveryOutcome.DISPUTED);

        verify(donorStatsRepository).incrementDisputed(DONOR);
        verifyNoMoreInteractions(donorStatsRepository);
    }

    @Test
    void recordOutcome_confirmedForNewDonor_createsStatsAndRepeatsIncrement() {
        given(donorStatsRepository.incrementConfirmed(DONOR)).willReturn(0, 1);

        service.recordOutcome(DONOR, DeliveryOutcome.CONFIRMED);

        ArgumentCaptor<DonorStats> captor = ArgumentCaptor.forClass(DonorStats.class);
        InOrder order = inOrder(donorStatsRepository);
        order.verify(donorStatsRepository).incrementConfirmed(DONOR);
        order.verify(donorStatsRepository).save(captor.capture());
        order.verify(donorStatsRepository).incrementConfirmed(DONOR);
        verifyNoMoreInteractions(donorStatsRepository);
        assertEquals(DONOR, captor.getValue().getDonorOrgId());
        assertEquals(0, captor.getValue().getConfirmedLots());
        assertEquals(0, captor.getValue().getDisputedLots());
    }

    @Test
    void recordOutcome_disputedForNewDonor_createsStatsAndRepeatsIncrement() {
        given(donorStatsRepository.incrementDisputed(DONOR)).willReturn(0, 1);

        service.recordOutcome(DONOR, DeliveryOutcome.DISPUTED);

        ArgumentCaptor<DonorStats> captor = ArgumentCaptor.forClass(DonorStats.class);
        InOrder order = inOrder(donorStatsRepository);
        order.verify(donorStatsRepository).incrementDisputed(DONOR);
        order.verify(donorStatsRepository).save(captor.capture());
        order.verify(donorStatsRepository).incrementDisputed(DONOR);
        verifyNoMoreInteractions(donorStatsRepository);
        assertEquals(DONOR, captor.getValue().getDonorOrgId());
    }
}
