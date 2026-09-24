package com.example.foodrescue.volunteer;

import com.example.foodrescue.common.FoodCategory;
import com.example.foodrescue.common.FoodLot;
import com.example.foodrescue.common.InvalidLotStateException;
import com.example.foodrescue.common.LotNotFoundException;
import com.example.foodrescue.common.LotRepository;
import com.example.foodrescue.common.LotStatus;
import com.example.foodrescue.common.LotStatusChanger;
import com.example.foodrescue.delivery.DeliveryOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class VolunteerServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-09-20T12:00:00Z");
    private static final UUID VOLUNTEER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID LOT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    @Mock
    private VolunteerRepository volunteerRepository;

    @Mock
    private LotRepository lotRepository;

    @Mock
    private LotStatusChanger statusChanger;

    private VolunteerServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new VolunteerServiceImpl(
                volunteerRepository,
                lotRepository,
                statusChanger,
                List.of(new TrustedAccess(), new StandardAccess(), new RestrictedAccess()),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    // --- Хелпери ---

    private VolunteerProfile volunteer(int completed, int latePickups, int noShows) {
        VolunteerProfile v = new VolunteerProfile();
        v.setId(VOLUNTEER_ID);
        v.setFullName("Тест Волонтер");
        v.setEmail("test@example.com");
        v.setPhone("+380501234567");
        v.setTransportType(TransportType.CAR);
        v.setActivityZone("Київ");
        v.setCompletedDeliveries(completed);
        v.setLatePickups(latePickups);
        v.setNoShows(noShows);
        return v;
    }

    private VolunteerProfile trustedVolunteer() {
        return volunteer(10, 0, 0);
    }

    private VolunteerProfile standardVolunteer() {
        return volunteer(8, 1, 1);
    }

    private VolunteerProfile restrictedVolunteer() {
        return volunteer(5, 0, 4);
    }

    private FoodLot publishedLot(FoodCategory category, BigDecimal weight, Instant publishedAt) {
        FoodLot lot = new FoodLot(LOT_ID);
        lot.setCategory(category);
        lot.setTotalWeightKg(weight);
        lot.setStatus(LotStatus.PUBLISHED);
        lot.setPublishedAt(publishedAt);
        lot.setDonorOrgId(UUID.randomUUID());
        return lot;
    }

    // --- create ---

    @Test
    void create_validRequest_savesProfile() {
        given(volunteerRepository.existsByEmail("new@example.com")).willReturn(false);
        given(volunteerRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        VolunteerRequest request = new VolunteerRequest(
                "Новий Волонтер", "new@example.com", "+380509876543", TransportType.BIKE, "Львів");
        VolunteerProfile created = service.create(request);

        assertNotNull(created.getId());
        assertEquals("Новий Волонтер", created.getFullName());
        assertEquals("new@example.com", created.getEmail());
        verify(volunteerRepository).existsByEmail("new@example.com");
        verify(volunteerRepository).save(created);
        verifyNoMoreInteractions(volunteerRepository);
        verifyNoInteractions(lotRepository, statusChanger);
    }

    @Test
    void create_duplicateEmail_throwsDuplicateVolunteerException() {
        given(volunteerRepository.existsByEmail("dup@example.com")).willReturn(true);

        VolunteerRequest request = new VolunteerRequest(
                "Дубль", "dup@example.com", "+380501111111", TransportType.CAR, "Київ");

        assertThrows(DuplicateVolunteerException.class, () -> service.create(request));

        verify(volunteerRepository).existsByEmail("dup@example.com");
        verify(volunteerRepository, never()).save(any());
        verifyNoMoreInteractions(volunteerRepository);
        verifyNoInteractions(lotRepository, statusChanger);
    }

    // --- getById ---

    @Test
    void getById_existingVolunteer_returnsIt() {
        VolunteerProfile v = trustedVolunteer();
        given(volunteerRepository.findById(VOLUNTEER_ID)).willReturn(Optional.of(v));

        assertSame(v, service.getById(VOLUNTEER_ID));

        verify(volunteerRepository).findById(VOLUNTEER_ID);
        verifyNoMoreInteractions(volunteerRepository);
        verifyNoInteractions(lotRepository, statusChanger);
    }

    @Test
    void getById_unknownVolunteer_throwsNotFoundException() {
        given(volunteerRepository.findById(VOLUNTEER_ID)).willReturn(Optional.empty());

        assertThrows(VolunteerNotFoundException.class, () -> service.getById(VOLUNTEER_ID));

        verify(volunteerRepository).findById(VOLUNTEER_ID);
        verifyNoMoreInteractions(volunteerRepository);
        verifyNoInteractions(lotRepository, statusChanger);
    }

    // --- reserve ---

    @Test
    void reserve_trustedVolunteer_reservesSuccessfully() {
        FoodLot lot = publishedLot(FoodCategory.BAKERY, BigDecimal.valueOf(5), NOW.minus(1, ChronoUnit.HOURS));
        VolunteerProfile v = trustedVolunteer();
        given(lotRepository.findById(LOT_ID)).willReturn(Optional.of(lot));
        given(volunteerRepository.findById(VOLUNTEER_ID)).willReturn(Optional.of(v));
        given(lotRepository.save(lot)).willReturn(lot);

        FoodLot result = service.reserve(LOT_ID, new ReservationRequest(VOLUNTEER_ID));

        assertEquals(VOLUNTEER_ID, result.getReservedByVolunteerId());
        assertEquals(NOW.plus(30, ChronoUnit.MINUTES), result.getReservedUntil());
        verify(statusChanger).transition(lot, LotStatus.RESERVED, "Зарезервовано");
        verify(lotRepository).findById(LOT_ID);
        verify(lotRepository).save(lot);
        verify(volunteerRepository).findById(VOLUNTEER_ID);
        verifyNoMoreInteractions(lotRepository, volunteerRepository, statusChanger);
    }

    @Test
    void reserve_restrictedVolunteerOnBakery_reservesSuccessfully() {
        FoodLot lot = publishedLot(FoodCategory.BAKERY, BigDecimal.valueOf(5), NOW.minus(1, ChronoUnit.HOURS));
        VolunteerProfile v = restrictedVolunteer();
        given(lotRepository.findById(LOT_ID)).willReturn(Optional.of(lot));
        given(volunteerRepository.findById(VOLUNTEER_ID)).willReturn(Optional.of(v));
        given(lotRepository.save(lot)).willReturn(lot);

        FoodLot result = service.reserve(LOT_ID, new ReservationRequest(VOLUNTEER_ID));

        assertEquals(VOLUNTEER_ID, result.getReservedByVolunteerId());
        verify(statusChanger).transition(lot, LotStatus.RESERVED, "Зарезервовано");
        verify(lotRepository).findById(LOT_ID);
        verify(lotRepository).save(lot);
        verify(volunteerRepository).findById(VOLUNTEER_ID);
        verifyNoMoreInteractions(lotRepository, volunteerRepository, statusChanger);
    }

    @Test
    void reserve_restrictedVolunteerOnPreparedMeal_throwsDenied() {
        FoodLot lot = publishedLot(FoodCategory.PREPARED_MEAL, BigDecimal.valueOf(5), NOW.minus(1, ChronoUnit.HOURS));
        VolunteerProfile v = restrictedVolunteer();
        given(lotRepository.findById(LOT_ID)).willReturn(Optional.of(lot));
        given(volunteerRepository.findById(VOLUNTEER_ID)).willReturn(Optional.of(v));

        assertThrows(ReservationDeniedException.class,
                () -> service.reserve(LOT_ID, new ReservationRequest(VOLUNTEER_ID)));

        verify(lotRepository).findById(LOT_ID);
        verify(volunteerRepository).findById(VOLUNTEER_ID);
        verify(lotRepository, never()).save(any());
        verifyNoMoreInteractions(lotRepository, volunteerRepository);
        verifyNoInteractions(statusChanger);
    }

    @Test
    void reserve_standardVolunteerBigLotInEarlyWindow_throwsDenied() {
        FoodLot lot = publishedLot(FoodCategory.BAKERY, BigDecimal.valueOf(25), NOW.minus(5, ChronoUnit.MINUTES));
        VolunteerProfile v = standardVolunteer();
        given(lotRepository.findById(LOT_ID)).willReturn(Optional.of(lot));
        given(volunteerRepository.findById(VOLUNTEER_ID)).willReturn(Optional.of(v));

        assertThrows(ReservationDeniedException.class,
                () -> service.reserve(LOT_ID, new ReservationRequest(VOLUNTEER_ID)));

        verify(lotRepository).findById(LOT_ID);
        verify(volunteerRepository).findById(VOLUNTEER_ID);
        verify(lotRepository, never()).save(any());
        verifyNoMoreInteractions(lotRepository, volunteerRepository);
        verifyNoInteractions(statusChanger);
    }

    @Test
    void reserve_standardVolunteerBigLotAfterEarlyWindow_reservesSuccessfully() {
        FoodLot lot = publishedLot(FoodCategory.BAKERY, BigDecimal.valueOf(25), NOW.minus(15, ChronoUnit.MINUTES));
        VolunteerProfile v = standardVolunteer();
        given(lotRepository.findById(LOT_ID)).willReturn(Optional.of(lot));
        given(volunteerRepository.findById(VOLUNTEER_ID)).willReturn(Optional.of(v));
        given(lotRepository.save(lot)).willReturn(lot);

        FoodLot result = service.reserve(LOT_ID, new ReservationRequest(VOLUNTEER_ID));

        assertEquals(VOLUNTEER_ID, result.getReservedByVolunteerId());
        verify(statusChanger).transition(lot, LotStatus.RESERVED, "Зарезервовано");
        verify(lotRepository).findById(LOT_ID);
        verify(lotRepository).save(lot);
        verify(volunteerRepository).findById(VOLUNTEER_ID);
        verifyNoMoreInteractions(lotRepository, volunteerRepository, statusChanger);
    }

    @Test
    void reserve_trustedVolunteerBigLotInEarlyWindow_reservesSuccessfully() {
        FoodLot lot = publishedLot(FoodCategory.BAKERY, BigDecimal.valueOf(25), NOW.minus(5, ChronoUnit.MINUTES));
        VolunteerProfile v = trustedVolunteer();
        given(lotRepository.findById(LOT_ID)).willReturn(Optional.of(lot));
        given(volunteerRepository.findById(VOLUNTEER_ID)).willReturn(Optional.of(v));
        given(lotRepository.save(lot)).willReturn(lot);

        FoodLot result = service.reserve(LOT_ID, new ReservationRequest(VOLUNTEER_ID));

        assertEquals(VOLUNTEER_ID, result.getReservedByVolunteerId());
        verify(statusChanger).transition(lot, LotStatus.RESERVED, "Зарезервовано");
        verify(lotRepository).findById(LOT_ID);
        verify(lotRepository).save(lot);
        verify(volunteerRepository).findById(VOLUNTEER_ID);
        verifyNoMoreInteractions(lotRepository, volunteerRepository, statusChanger);
    }

    @Test
    void reserve_lotNotFound_throwsLotNotFoundException() {
        given(lotRepository.findById(LOT_ID)).willReturn(Optional.empty());

        assertThrows(LotNotFoundException.class,
                () -> service.reserve(LOT_ID, new ReservationRequest(VOLUNTEER_ID)));

        verify(lotRepository).findById(LOT_ID);
        verifyNoMoreInteractions(lotRepository);
        verifyNoInteractions(volunteerRepository, statusChanger);
    }

    @Test
    void reserve_volunteerNotFound_throwsVolunteerNotFoundException() {
        FoodLot lot = publishedLot(FoodCategory.BAKERY, BigDecimal.valueOf(5), NOW.minus(1, ChronoUnit.HOURS));
        given(lotRepository.findById(LOT_ID)).willReturn(Optional.of(lot));
        given(volunteerRepository.findById(VOLUNTEER_ID)).willReturn(Optional.empty());

        assertThrows(VolunteerNotFoundException.class,
                () -> service.reserve(LOT_ID, new ReservationRequest(VOLUNTEER_ID)));

        verify(lotRepository).findById(LOT_ID);
        verify(volunteerRepository).findById(VOLUNTEER_ID);
        verify(lotRepository, never()).save(any());
        verifyNoMoreInteractions(lotRepository, volunteerRepository);
        verifyNoInteractions(statusChanger);
    }

    @Test
    void reserve_invalidTransition_doesNotSaveOrSetFields() {
        FoodLot lot = publishedLot(FoodCategory.BAKERY, BigDecimal.valueOf(5), NOW.minus(1, ChronoUnit.HOURS));
        VolunteerProfile v = trustedVolunteer();
        given(lotRepository.findById(LOT_ID)).willReturn(Optional.of(lot));
        given(volunteerRepository.findById(VOLUNTEER_ID)).willReturn(Optional.of(v));
        doThrow(new InvalidLotStateException("перехід заборонено"))
                .when(statusChanger).transition(lot, LotStatus.RESERVED, "Зарезервовано");

        assertThrows(InvalidLotStateException.class,
                () -> service.reserve(LOT_ID, new ReservationRequest(VOLUNTEER_ID)));

        assertNull(lot.getReservedByVolunteerId());
        verify(lotRepository).findById(LOT_ID);
        verify(volunteerRepository).findById(VOLUNTEER_ID);
        verify(statusChanger).transition(lot, LotStatus.RESERVED, "Зарезервовано");
        verify(lotRepository, never()).save(any());
        verifyNoMoreInteractions(lotRepository, volunteerRepository, statusChanger);
    }

    // --- cancelReservation ---

    @Test
    void cancelReservation_reservedLot_clearsReservationAndSaves() {
        FoodLot lot = publishedLot(FoodCategory.BAKERY, BigDecimal.valueOf(5), NOW.minus(1, ChronoUnit.HOURS));
        lot.setStatus(LotStatus.RESERVED);
        lot.setReservedByVolunteerId(VOLUNTEER_ID);
        lot.setReservedUntil(NOW.plus(30, ChronoUnit.MINUTES));
        given(lotRepository.findById(LOT_ID)).willReturn(Optional.of(lot));
        given(lotRepository.save(lot)).willReturn(lot);

        FoodLot result = service.cancelReservation(LOT_ID);

        assertNull(result.getReservedByVolunteerId());
        assertNull(result.getReservedUntil());
        verify(statusChanger).transition(lot, LotStatus.PUBLISHED, "Резерв скасовано");
        verify(lotRepository).findById(LOT_ID);
        verify(lotRepository).save(lot);
        verifyNoMoreInteractions(lotRepository, statusChanger);
        verifyNoInteractions(volunteerRepository);
    }

    @Test
    void cancelReservation_lotNotFound_throwsLotNotFoundException() {
        given(lotRepository.findById(LOT_ID)).willReturn(Optional.empty());

        assertThrows(LotNotFoundException.class, () -> service.cancelReservation(LOT_ID));

        verify(lotRepository).findById(LOT_ID);
        verifyNoMoreInteractions(lotRepository);
        verifyNoInteractions(volunteerRepository, statusChanger);
    }

    @Test
    void cancelReservation_invalidTransition_doesNotSave() {
        FoodLot lot = publishedLot(FoodCategory.BAKERY, BigDecimal.valueOf(5), NOW.minus(1, ChronoUnit.HOURS));
        given(lotRepository.findById(LOT_ID)).willReturn(Optional.of(lot));
        doThrow(new InvalidLotStateException("перехід заборонено"))
                .when(statusChanger).transition(lot, LotStatus.PUBLISHED, "Резерв скасовано");

        assertThrows(InvalidLotStateException.class, () -> service.cancelReservation(LOT_ID));

        verify(lotRepository).findById(LOT_ID);
        verify(statusChanger).transition(lot, LotStatus.PUBLISHED, "Резерв скасовано");
        verify(lotRepository, never()).save(any());
        verifyNoMoreInteractions(lotRepository, statusChanger);
        verifyNoInteractions(volunteerRepository);
    }

    // --- recordOutcome ---

    @Test
    void recordOutcome_confirmed_incrementsCompletedDeliveries() {
        VolunteerProfile v = volunteer(5, 0, 0);
        given(volunteerRepository.findById(VOLUNTEER_ID)).willReturn(Optional.of(v));
        given(volunteerRepository.save(v)).willReturn(v);

        service.recordOutcome(VOLUNTEER_ID, DeliveryOutcome.CONFIRMED, false);

        assertEquals(6, v.getCompletedDeliveries());
        assertEquals(0, v.getNoShows());
        assertEquals(0, v.getLatePickups());
        verify(volunteerRepository).findById(VOLUNTEER_ID);
        verify(volunteerRepository).save(v);
        verifyNoMoreInteractions(volunteerRepository);
        verifyNoInteractions(lotRepository, statusChanger);
    }

    @Test
    void recordOutcome_disputed_incrementsNoShows() {
        VolunteerProfile v = volunteer(5, 0, 1);
        given(volunteerRepository.findById(VOLUNTEER_ID)).willReturn(Optional.of(v));
        given(volunteerRepository.save(v)).willReturn(v);

        service.recordOutcome(VOLUNTEER_ID, DeliveryOutcome.DISPUTED, false);

        assertEquals(5, v.getCompletedDeliveries());
        assertEquals(2, v.getNoShows());
        assertEquals(0, v.getLatePickups());
        verify(volunteerRepository).findById(VOLUNTEER_ID);
        verify(volunteerRepository).save(v);
        verifyNoMoreInteractions(volunteerRepository);
        verifyNoInteractions(lotRepository, statusChanger);
    }

    @Test
    void recordOutcome_latePickup_incrementsLatePickups() {
        VolunteerProfile v = volunteer(5, 1, 0);
        given(volunteerRepository.findById(VOLUNTEER_ID)).willReturn(Optional.of(v));
        given(volunteerRepository.save(v)).willReturn(v);

        service.recordOutcome(VOLUNTEER_ID, DeliveryOutcome.CONFIRMED, true);

        assertEquals(6, v.getCompletedDeliveries());
        assertEquals(2, v.getLatePickups());
        verify(volunteerRepository).findById(VOLUNTEER_ID);
        verify(volunteerRepository).save(v);
        verifyNoMoreInteractions(volunteerRepository);
        verifyNoInteractions(lotRepository, statusChanger);
    }

    @Test
    void recordOutcome_volunteerNotFound_throwsNotFoundAndDoesNotSave() {
        given(volunteerRepository.findById(VOLUNTEER_ID)).willReturn(Optional.empty());

        assertThrows(VolunteerNotFoundException.class,
                () -> service.recordOutcome(VOLUNTEER_ID, DeliveryOutcome.CONFIRMED, false));

        verify(volunteerRepository).findById(VOLUNTEER_ID);
        verify(volunteerRepository, never()).save(any());
        verifyNoMoreInteractions(volunteerRepository);
        verifyNoInteractions(lotRepository, statusChanger);
    }
}
