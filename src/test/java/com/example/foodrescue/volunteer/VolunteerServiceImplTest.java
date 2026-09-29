package com.example.foodrescue.volunteer;

import com.example.foodrescue.common.FoodCategory;
import com.example.foodrescue.common.FoodLot;
import com.example.foodrescue.common.InvalidLotStateException;
import com.example.foodrescue.common.LotNotFoundException;
import com.example.foodrescue.common.LotRepository;
import com.example.foodrescue.common.LotStatus;
import com.example.foodrescue.common.LotStatusChanger;
import com.example.foodrescue.common.NotFoundException;
import com.example.foodrescue.delivery.DeliveryOutcome;
import com.example.foodrescue.delivery.DestinationPoint;
import com.example.foodrescue.delivery.DestinationPointRepository;
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
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    private static final UUID POINT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

    @Mock
    private VolunteerRepository volunteerRepository;

    @Mock
    private LotRepository lotRepository;

    @Mock
    private LotStatusChanger statusChanger;

    @Mock
    private DestinationPointRepository destinationPointRepository;

    private VolunteerServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new VolunteerServiceImpl(
                volunteerRepository,
                lotRepository,
                statusChanger,
                destinationPointRepository,
                List.of(new TrustedAccess(), new StandardAccess(), new RestrictedAccess()),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    // --- Хелпери ---

    private VolunteerProfile volunteer(int completed, int latePickups, int noShows) {
        VolunteerProfile v = new VolunteerProfile(VOLUNTEER_ID);
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
    void create_validRequest_savesAndReturnsResponse() {
        given(volunteerRepository.existsByEmail("new@example.com")).willReturn(false);
        given(volunteerRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        VolunteerRequest request = new VolunteerRequest(
                "Новий Волонтер", "new@example.com", "+380509876543", TransportType.BIKE, "Львів");
        VolunteerResponse response = service.create(request);

        assertNotNull(response.id());
        assertEquals("Новий Волонтер", response.fullName());
        assertEquals("new@example.com", response.email());
        assertEquals(TransportType.BIKE, response.transportType());
        verify(volunteerRepository).existsByEmail("new@example.com");
        verify(volunteerRepository).save(any());
        verifyNoMoreInteractions(volunteerRepository);
        verifyNoInteractions(lotRepository, statusChanger, destinationPointRepository);
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
        verifyNoInteractions(lotRepository, statusChanger, destinationPointRepository);
    }

    // --- getById ---

    @Test
    void getById_existingVolunteer_returnsResponse() {
        VolunteerProfile v = trustedVolunteer();
        given(volunteerRepository.findByIdWithPoints(VOLUNTEER_ID)).willReturn(Optional.of(v));

        VolunteerResponse response = service.getById(VOLUNTEER_ID);

        assertEquals(VOLUNTEER_ID, response.id());
        assertEquals("Тест Волонтер", response.fullName());
        verify(volunteerRepository).findByIdWithPoints(VOLUNTEER_ID);
        verifyNoMoreInteractions(volunteerRepository);
        verifyNoInteractions(lotRepository, statusChanger, destinationPointRepository);
    }

    @Test
    void getById_unknownVolunteer_throwsNotFoundException() {
        given(volunteerRepository.findByIdWithPoints(VOLUNTEER_ID)).willReturn(Optional.empty());

        assertThrows(VolunteerNotFoundException.class, () -> service.getById(VOLUNTEER_ID));

        verify(volunteerRepository).findByIdWithPoints(VOLUNTEER_ID);
        verifyNoMoreInteractions(volunteerRepository);
        verifyNoInteractions(lotRepository, statusChanger, destinationPointRepository);
    }

    // --- getAll ---

    @Test
    void getAll_returnsAllVolunteers() {
        VolunteerProfile v = trustedVolunteer();
        given(volunteerRepository.findAllWithPoints()).willReturn(List.of(v));

        List<VolunteerResponse> result = service.getAll();

        assertEquals(1, result.size());
        assertEquals("Тест Волонтер", result.get(0).fullName());
        verify(volunteerRepository).findAllWithPoints();
        verifyNoMoreInteractions(volunteerRepository);
    }

    // --- update ---

    @Test
    void update_validRequest_updatesAndReturnsResponse() {
        VolunteerProfile v = trustedVolunteer();
        given(volunteerRepository.findByIdWithPoints(VOLUNTEER_ID)).willReturn(Optional.of(v));
        given(volunteerRepository.existsByEmailAndIdNot("updated@example.com", VOLUNTEER_ID)).willReturn(false);
        given(volunteerRepository.save(v)).willReturn(v);

        VolunteerRequest request = new VolunteerRequest(
                "Оновлений", "updated@example.com", "+380507777777", TransportType.BIKE, "Одеса");
        VolunteerResponse response = service.update(VOLUNTEER_ID, request);

        assertEquals("Оновлений", response.fullName());
        assertEquals("updated@example.com", response.email());
        verify(volunteerRepository).findByIdWithPoints(VOLUNTEER_ID);
        verify(volunteerRepository).existsByEmailAndIdNot("updated@example.com", VOLUNTEER_ID);
        verify(volunteerRepository).save(v);
    }

    @Test
    void update_duplicateEmail_throwsDuplicateVolunteerException() {
        VolunteerProfile v = trustedVolunteer();
        given(volunteerRepository.findByIdWithPoints(VOLUNTEER_ID)).willReturn(Optional.of(v));
        given(volunteerRepository.existsByEmailAndIdNot("taken@example.com", VOLUNTEER_ID)).willReturn(true);

        VolunteerRequest request = new VolunteerRequest(
                "Тест", "taken@example.com", "+380501234567", TransportType.CAR, "Київ");

        assertThrows(DuplicateVolunteerException.class, () -> service.update(VOLUNTEER_ID, request));

        verify(volunteerRepository, never()).save(any());
    }

    @Test
    void update_unknownVolunteer_throwsNotFoundException() {
        given(volunteerRepository.findByIdWithPoints(VOLUNTEER_ID)).willReturn(Optional.empty());

        VolunteerRequest request = new VolunteerRequest(
                "Тест", "test@example.com", "+380501234567", TransportType.CAR, "Київ");

        assertThrows(VolunteerNotFoundException.class, () -> service.update(VOLUNTEER_ID, request));
    }

    // --- delete ---

    @Test
    void delete_noActiveLots_deletesVolunteer() {
        VolunteerProfile v = trustedVolunteer();
        given(volunteerRepository.findById(VOLUNTEER_ID)).willReturn(Optional.of(v));
        given(lotRepository.existsByReservedByVolunteerIdAndStatusIn(
                eq(VOLUNTEER_ID), any())).willReturn(false);

        service.delete(VOLUNTEER_ID);

        verify(volunteerRepository).delete(v);
    }

    @Test
    void delete_hasActiveLots_throwsConflict() {
        VolunteerProfile v = trustedVolunteer();
        given(volunteerRepository.findById(VOLUNTEER_ID)).willReturn(Optional.of(v));
        given(lotRepository.existsByReservedByVolunteerIdAndStatusIn(
                VOLUNTEER_ID, EnumSet.of(LotStatus.RESERVED, LotStatus.PICKED_UP))).willReturn(true);

        assertThrows(VolunteerHasActiveLotsException.class, () -> service.delete(VOLUNTEER_ID));

        verify(volunteerRepository, never()).delete(any());
    }

    @Test
    void delete_unknownVolunteer_throwsNotFoundException() {
        given(volunteerRepository.findById(VOLUNTEER_ID)).willReturn(Optional.empty());

        assertThrows(VolunteerNotFoundException.class, () -> service.delete(VOLUNTEER_ID));
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
    }

    @Test
    void reserve_restrictedVolunteerOnPreparedMeal_throwsDenied() {
        FoodLot lot = publishedLot(FoodCategory.PREPARED_MEAL, BigDecimal.valueOf(5), NOW.minus(1, ChronoUnit.HOURS));
        VolunteerProfile v = restrictedVolunteer();
        given(lotRepository.findById(LOT_ID)).willReturn(Optional.of(lot));
        given(volunteerRepository.findById(VOLUNTEER_ID)).willReturn(Optional.of(v));

        assertThrows(ReservationDeniedException.class,
                () -> service.reserve(LOT_ID, new ReservationRequest(VOLUNTEER_ID)));

        verify(lotRepository, never()).save(any());
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

        verify(lotRepository, never()).save(any());
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
    }

    @Test
    void reserve_lotNotFound_throwsLotNotFoundException() {
        given(lotRepository.findById(LOT_ID)).willReturn(Optional.empty());

        assertThrows(LotNotFoundException.class,
                () -> service.reserve(LOT_ID, new ReservationRequest(VOLUNTEER_ID)));

        verifyNoInteractions(volunteerRepository, statusChanger);
    }

    @Test
    void reserve_volunteerNotFound_throwsVolunteerNotFoundException() {
        FoodLot lot = publishedLot(FoodCategory.BAKERY, BigDecimal.valueOf(5), NOW.minus(1, ChronoUnit.HOURS));
        given(lotRepository.findById(LOT_ID)).willReturn(Optional.of(lot));
        given(volunteerRepository.findById(VOLUNTEER_ID)).willReturn(Optional.empty());

        assertThrows(VolunteerNotFoundException.class,
                () -> service.reserve(LOT_ID, new ReservationRequest(VOLUNTEER_ID)));

        verify(lotRepository, never()).save(any());
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
        verify(lotRepository, never()).save(any());
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
        verify(lotRepository).save(lot);
        verifyNoInteractions(volunteerRepository);
    }

    @Test
    void cancelReservation_lotNotFound_throwsLotNotFoundException() {
        given(lotRepository.findById(LOT_ID)).willReturn(Optional.empty());

        assertThrows(LotNotFoundException.class, () -> service.cancelReservation(LOT_ID));

        verifyNoInteractions(volunteerRepository, statusChanger);
    }

    @Test
    void cancelReservation_invalidTransition_doesNotSave() {
        FoodLot lot = publishedLot(FoodCategory.BAKERY, BigDecimal.valueOf(5), NOW.minus(1, ChronoUnit.HOURS));
        given(lotRepository.findById(LOT_ID)).willReturn(Optional.of(lot));
        doThrow(new InvalidLotStateException("перехід заборонено"))
                .when(statusChanger).transition(lot, LotStatus.PUBLISHED, "Резерв скасовано");

        assertThrows(InvalidLotStateException.class, () -> service.cancelReservation(LOT_ID));

        verify(lotRepository, never()).save(any());
        verifyNoInteractions(volunteerRepository);
    }

    // --- recordOutcome ---

    @Test
    void recordOutcome_confirmed_callsIncrementCompleted() {
        given(volunteerRepository.existsById(VOLUNTEER_ID)).willReturn(true);

        service.recordOutcome(VOLUNTEER_ID, DeliveryOutcome.CONFIRMED, false);

        verify(volunteerRepository).incrementCompleted(VOLUNTEER_ID);
        verify(volunteerRepository, never()).incrementNoShows(any());
        verify(volunteerRepository, never()).incrementLatePickups(any());
    }

    @Test
    void recordOutcome_disputed_callsIncrementNoShows() {
        given(volunteerRepository.existsById(VOLUNTEER_ID)).willReturn(true);

        service.recordOutcome(VOLUNTEER_ID, DeliveryOutcome.DISPUTED, false);

        verify(volunteerRepository).incrementNoShows(VOLUNTEER_ID);
        verify(volunteerRepository, never()).incrementCompleted(any());
        verify(volunteerRepository, never()).incrementLatePickups(any());
    }

    @Test
    void recordOutcome_latePickup_callsIncrementLatePickups() {
        given(volunteerRepository.existsById(VOLUNTEER_ID)).willReturn(true);

        service.recordOutcome(VOLUNTEER_ID, DeliveryOutcome.CONFIRMED, true);

        verify(volunteerRepository).incrementCompleted(VOLUNTEER_ID);
        verify(volunteerRepository).incrementLatePickups(VOLUNTEER_ID);
    }

    @Test
    void recordOutcome_volunteerNotFound_throwsNotFound() {
        given(volunteerRepository.existsById(VOLUNTEER_ID)).willReturn(false);

        assertThrows(VolunteerNotFoundException.class,
                () -> service.recordOutcome(VOLUNTEER_ID, DeliveryOutcome.CONFIRMED, false));

        verify(volunteerRepository, never()).incrementCompleted(any());
        verify(volunteerRepository, never()).incrementNoShows(any());
    }

    // --- addPreferredPoint ---

    @Test
    void addPreferredPoint_validData_addsPointAndReturnsResponse() {
        VolunteerProfile v = trustedVolunteer();
        DestinationPoint point = new DestinationPoint(
                POINT_ID, UUID.randomUUID(), "Центр допомоги",
                "вул. Хрещатик, 1", "09:00-18:00", Set.of());
        given(volunteerRepository.findByIdWithPoints(VOLUNTEER_ID)).willReturn(Optional.of(v));
        given(destinationPointRepository.findById(POINT_ID)).willReturn(Optional.of(point));
        given(volunteerRepository.save(v)).willReturn(v);

        PreferredPointResponse response = service.addPreferredPoint(VOLUNTEER_ID, POINT_ID);

        assertEquals(POINT_ID, response.id());
        assertEquals("Центр допомоги", response.name());
        assertTrue(v.getPreferredPoints().contains(point));
        verify(volunteerRepository).save(v);
    }

    @Test
    void addPreferredPoint_volunteerNotFound_throwsNotFound() {
        given(volunteerRepository.findByIdWithPoints(VOLUNTEER_ID)).willReturn(Optional.empty());

        assertThrows(VolunteerNotFoundException.class,
                () -> service.addPreferredPoint(VOLUNTEER_ID, POINT_ID));

        verifyNoInteractions(destinationPointRepository);
    }

    @Test
    void addPreferredPoint_pointNotFound_throwsNotFound() {
        VolunteerProfile v = trustedVolunteer();
        given(volunteerRepository.findByIdWithPoints(VOLUNTEER_ID)).willReturn(Optional.of(v));
        given(destinationPointRepository.findById(POINT_ID)).willReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> service.addPreferredPoint(VOLUNTEER_ID, POINT_ID));

        verify(volunteerRepository, never()).save(any());
    }

    // --- removePreferredPoint ---

    @Test
    void removePreferredPoint_existingPoint_removesIt() {
        VolunteerProfile v = trustedVolunteer();
        DestinationPoint point = new DestinationPoint(
                POINT_ID, UUID.randomUUID(), "Центр допомоги",
                "вул. Хрещатик, 1", "09:00-18:00", Set.of());
        v.getPreferredPoints().add(point);
        given(volunteerRepository.findByIdWithPoints(VOLUNTEER_ID)).willReturn(Optional.of(v));
        given(volunteerRepository.save(v)).willReturn(v);

        service.removePreferredPoint(VOLUNTEER_ID, POINT_ID);

        assertTrue(v.getPreferredPoints().isEmpty());
        verify(volunteerRepository).save(v);
    }

    @Test
    void removePreferredPoint_pointNotInList_throwsNotFound() {
        VolunteerProfile v = trustedVolunteer();
        given(volunteerRepository.findByIdWithPoints(VOLUNTEER_ID)).willReturn(Optional.of(v));

        assertThrows(NotFoundException.class,
                () -> service.removePreferredPoint(VOLUNTEER_ID, POINT_ID));

        verify(volunteerRepository, never()).save(any());
    }

    // --- getPreferredPoints ---

    @Test
    void getPreferredPoints_returnsPointsList() {
        VolunteerProfile v = trustedVolunteer();
        DestinationPoint point = new DestinationPoint(
                POINT_ID, UUID.randomUUID(), "Центр допомоги",
                "вул. Хрещатик, 1", "09:00-18:00", Set.of());
        v.getPreferredPoints().add(point);
        given(volunteerRepository.findByIdWithPoints(VOLUNTEER_ID)).willReturn(Optional.of(v));

        List<PreferredPointResponse> result = service.getPreferredPoints(VOLUNTEER_ID);

        assertEquals(1, result.size());
        assertEquals("Центр допомоги", result.get(0).name());
    }
}
