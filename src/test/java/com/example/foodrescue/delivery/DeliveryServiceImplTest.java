package com.example.foodrescue.delivery;

import com.example.foodrescue.common.CurrentUser;
import com.example.foodrescue.common.ForbiddenActionException;
import com.example.foodrescue.common.FoodCategory;
import com.example.foodrescue.common.FoodLot;
import com.example.foodrescue.common.LotRepository;
import com.example.foodrescue.common.LotStatus;
import com.example.foodrescue.common.LotStatusChanger;
import com.example.foodrescue.common.StatusHistoryRecorder;
import com.example.foodrescue.delivery.internal.Delivery;
import com.example.foodrescue.delivery.internal.DeliveryRepository;
import com.example.foodrescue.delivery.internal.DeliveryServiceImpl;
import com.example.foodrescue.delivery.internal.exception.DestinationPointInUseException;
import com.example.foodrescue.delivery.internal.exception.DuplicateDestinationPointException;
import com.example.foodrescue.delivery.internal.exception.ConfirmationAttemptsExceededException;
import com.example.foodrescue.delivery.internal.exception.InvalidConfirmationCodeException;
import com.example.foodrescue.delivery.internal.strategy.BakeryTolerance;
import com.example.foodrescue.delivery.internal.strategy.GroceryTolerance;
import com.example.foodrescue.delivery.internal.strategy.PreparedMealTolerance;
import com.example.foodrescue.delivery.internal.strategy.VegetablesTolerance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class DeliveryServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-09-20T12:00:00Z");
    private static final UUID LOT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID DONOR_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final UUID VOLUNTEER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID POINT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

    @Mock private LotRepository lotRepository;
    @Mock private LotStatusChanger statusChanger;
    @Mock private StatusHistoryRecorder historyRecorder;
    @Mock private DestinationPointRepository destinationPointRepository;
    @Mock private DeliveryRepository deliveryRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private CurrentUser currentUser;

    private DeliveryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DeliveryServiceImpl(
                lotRepository,
                statusChanger,
                historyRecorder,
                destinationPointRepository,
                deliveryRepository,
                List.of(new PreparedMealTolerance(), new BakeryTolerance(), new VegetablesTolerance(), new GroceryTolerance()),
                eventPublisher,
                Clock.fixed(NOW, ZoneOffset.UTC),
                currentUser);
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    private void asCoordinator() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("coordinator1", null,
                        List.of(new SimpleGrantedAuthority("ROLE_COORDINATOR"))));
    }

    private void asVolunteer(UUID profileId) {
        given(currentUser.profileId()).willReturn(profileId);
    }

    private FoodLot lot(LotStatus status, FoodCategory category) {
        FoodLot lot = new FoodLot(LOT_ID);
        lot.setDonorOrgId(DONOR_ID);
        lot.setReservedByVolunteerId(VOLUNTEER_ID);
        lot.setCategory(category);
        lot.setStatus(status);
        return lot;
    }

    private DestinationPoint point(Set<FoodCategory> categories) {
        return new DestinationPoint(
                POINT_ID,
                UUID.randomUUID(),
                "Благодійний центр",
                "вул. Центральна, 10",
                "09:00-18:00",
                categories);
    }

    private Delivery delivery(FoodLot lot) {
        Delivery delivery = new Delivery(UUID.randomUUID(), lot, VOLUNTEER_ID, NOW, new BigDecimal("10.00"), false);
        delivery.setDestinationPoint(point(Set.of(FoodCategory.BAKERY)));
        delivery.setDeliveredAt(NOW);
        delivery.setConfirmationCode("123456");
        return delivery;
    }

    @Test
    void createDestinationPoint_uniqueName_savesAndReturnsDto() {
        DestinationPointRequest request = new DestinationPointRequest(
                UUID.randomUUID(), "Благодійний центр", "вул. Центральна, 10", "09:00-18:00", Set.of(FoodCategory.BAKERY));
        given(destinationPointRepository.existsByName(request.name())).willReturn(false);
        given(destinationPointRepository.save(any(DestinationPoint.class))).willAnswer(invocation -> invocation.getArgument(0));

        DestinationPointResponse result = service.createDestinationPoint(request);

        assertEquals(request.name(), result.name());
        assertEquals(request.acceptedCategories(), result.acceptedCategories());
        verify(destinationPointRepository).existsByName(request.name());
        verify(destinationPointRepository).save(any(DestinationPoint.class));
    }

    @Test
    void updateDestinationPoint_duplicateName_throwsAndDoesNotSave() {
        DestinationPoint existing = point(Set.of(FoodCategory.BAKERY));
        DestinationPointRequest request = new DestinationPointRequest(
                UUID.randomUUID(), "Інший центр", "вул. Нова, 1", "10:00-19:00", Set.of(FoodCategory.GROCERY));
        given(destinationPointRepository.findByIdWithCategories(POINT_ID)).willReturn(Optional.of(existing));
        given(destinationPointRepository.existsByNameAndIdNot(request.name(), POINT_ID)).willReturn(true);

        assertThrows(DuplicateDestinationPointException.class,
                () -> service.updateDestinationPoint(POINT_ID, request));

        verify(destinationPointRepository, never()).save(any());
    }

    @Test
    void deleteDestinationPoint_usedByDelivery_throwsAndDoesNotDelete() {
        DestinationPoint existing = point(Set.of(FoodCategory.BAKERY));
        given(destinationPointRepository.findById(POINT_ID)).willReturn(Optional.of(existing));
        given(deliveryRepository.existsByDestinationPointId(POINT_ID)).willReturn(true);

        assertThrows(DestinationPointInUseException.class,
                () -> service.deleteDestinationPoint(POINT_ID));

        verify(destinationPointRepository, never()).delete(any());
    }

    @Test
    void pickup_reservedLot_createsJpaDelivery() {
        asVolunteer(VOLUNTEER_ID);
        FoodLot lot = lot(LotStatus.RESERVED, FoodCategory.BAKERY);
        given(lotRepository.findById(LOT_ID)).willReturn(Optional.of(lot));
        doAnswer(invocation -> {
            lot.setStatus(LotStatus.PICKED_UP);
            return null;
        }).when(statusChanger).transition(lot, LotStatus.PICKED_UP, "Лот передано волонтеру");
        ArgumentCaptor<Delivery> captor = ArgumentCaptor.forClass(Delivery.class);

        DeliveryResponse response = service.pickup(LOT_ID, new PickupRequest(new BigDecimal("12.50")));

        verify(deliveryRepository).save(captor.capture());
        assertEquals(lot, captor.getValue().getLot());
        assertEquals(VOLUNTEER_ID, captor.getValue().getVolunteerId());
        assertEquals(LotStatus.PICKED_UP, response.lotStatus());
        verify(lotRepository).save(lot);
    }

    @Test
    void deliver_acceptedCategory_updatesExistingDelivery() {
        asVolunteer(VOLUNTEER_ID);
        FoodLot lot = lot(LotStatus.PICKED_UP, FoodCategory.BAKERY);
        Delivery delivery = delivery(lot);
        DestinationPoint point = point(Set.of(FoodCategory.BAKERY));
        given(deliveryRepository.findByLotIdWithLot(LOT_ID)).willReturn(Optional.of(delivery));
        given(destinationPointRepository.findByIdWithCategories(POINT_ID)).willReturn(Optional.of(point));
        doAnswer(invocation -> {
            lot.setStatus(LotStatus.DELIVERED);
            return null;
        }).when(statusChanger).transition(lot, LotStatus.DELIVERED, "Доставлено до пункту призначення");

        DeliveryResponse response = service.deliver(LOT_ID, new DeliveryRequest(POINT_ID));

        assertEquals(POINT_ID, response.destinationPointId());
        assertEquals(LotStatus.DELIVERED, response.lotStatus());
        verify(deliveryRepository).save(delivery);
        verify(lotRepository).save(lot);
    }

    @Test
    void confirm_validCode_publishesEventLast() {
        asVolunteer(VOLUNTEER_ID);
        FoodLot lot = lot(LotStatus.DELIVERED, FoodCategory.BAKERY);
        Delivery delivery = delivery(lot);
        given(deliveryRepository.findByLotIdWithLot(LOT_ID)).willReturn(Optional.of(delivery));
        doAnswer(invocation -> {
            lot.setStatus(LotStatus.CONFIRMED);
            return null;
        }).when(statusChanger).transition(lot, LotStatus.CONFIRMED, "Отримання підтверджено");

        DeliveryResponse response = service.confirm(LOT_ID, new ConfirmationRequest("123456", new BigDecimal("10.00")));

        assertEquals(LotStatus.CONFIRMED, response.lotStatus());
        assertEquals(new BigDecimal("10.00"), response.receivedWeightKg());
        verify(deliveryRepository).save(delivery);
        verify(lotRepository).save(lot);
        verify(eventPublisher).publishEvent(any(DeliveryFinishedEvent.class));
    }

    @Test
    void getDeliveries_usesJoinFetchRepositoryMethod() {
        asCoordinator();
        FoodLot lot = lot(LotStatus.DELIVERED, FoodCategory.BAKERY);
        given(deliveryRepository.findAllWithLotAndPoint()).willReturn(List.of(delivery(lot)));

        List<DeliveryResponse> result = service.getDeliveries();

        assertEquals(1, result.size());
        verify(deliveryRepository).findAllWithLotAndPoint();
        verifyNoMoreInteractions(deliveryRepository);
    }


    @Test
    void pickup_anotherVolunteer_isForbiddenBeforeStatusChange() {
        UUID stranger = UUID.randomUUID();
        asVolunteer(stranger);
        FoodLot lot = lot(LotStatus.RESERVED, FoodCategory.BAKERY);
        given(lotRepository.findById(LOT_ID)).willReturn(Optional.of(lot));

        assertThrows(ForbiddenActionException.class,
                () -> service.pickup(LOT_ID, new PickupRequest(new BigDecimal("10.00"))));

        verifyNoMoreInteractions(deliveryRepository, statusChanger);
        assertEquals(LotStatus.RESERVED, lot.getStatus());
    }

    @Test
    void deliver_anotherVolunteer_isForbiddenBeforeDestinationLookup() {
        asVolunteer(UUID.randomUUID());
        FoodLot lot = lot(LotStatus.PICKED_UP, FoodCategory.BAKERY);
        given(deliveryRepository.findByLotIdWithLot(LOT_ID)).willReturn(Optional.of(delivery(lot)));

        assertThrows(ForbiddenActionException.class,
                () -> service.deliver(LOT_ID, new DeliveryRequest(POINT_ID)));

        verifyNoMoreInteractions(destinationPointRepository, statusChanger);
    }

    @Test
    void confirm_anotherVolunteer_isForbiddenEvenWithCorrectCode() {
        asVolunteer(UUID.randomUUID());
        FoodLot lot = lot(LotStatus.DELIVERED, FoodCategory.BAKERY);
        Delivery delivery = delivery(lot);
        given(deliveryRepository.findByLotIdWithLot(LOT_ID)).willReturn(Optional.of(delivery));

        assertThrows(ForbiddenActionException.class,
                () -> service.confirm(LOT_ID, new ConfirmationRequest("123456", new BigDecimal("10.00"))));

        assertEquals(0, delivery.getFailedConfirmationAttempts());
        verify(deliveryRepository, never()).save(any());
        verifyNoMoreInteractions(eventPublisher, statusChanger);
    }

    @Test
    void getDeliveries_volunteerSeesOnlyOwnDeliveries() {
        asVolunteer(VOLUNTEER_ID);
        given(deliveryRepository.findAllByVolunteerIdWithLotAndPoint(VOLUNTEER_ID))
                .willReturn(List.of(delivery(lot(LotStatus.DELIVERED, FoodCategory.BAKERY))));

        List<DeliveryResponse> result = service.getDeliveries();

        assertEquals(1, result.size());
        assertEquals(VOLUNTEER_ID, result.getFirst().volunteerId());
        verify(deliveryRepository, never()).findAllWithLotAndPoint();
    }

    @Test
    void getDelivery_anotherVolunteer_isForbidden() {
        asVolunteer(UUID.randomUUID());
        given(deliveryRepository.findByLotIdWithLot(LOT_ID))
                .willReturn(Optional.of(delivery(lot(LotStatus.DELIVERED, FoodCategory.BAKERY))));

        assertThrows(ForbiddenActionException.class, () -> service.getDelivery(LOT_ID));
    }

    @Test
    void getDelivery_coordinatorCanSeeAnyDelivery() {
        asCoordinator();
        given(deliveryRepository.findByLotIdWithLot(LOT_ID))
                .willReturn(Optional.of(delivery(lot(LotStatus.DELIVERED, FoodCategory.BAKERY))));

        assertEquals(VOLUNTEER_ID, service.getDelivery(LOT_ID).volunteerId());
    }

    @Test
    void confirm_fiveWrongCodes_lockTheDeliveryAndPersistEachAttempt() {
        asVolunteer(VOLUNTEER_ID);
        Delivery delivery = delivery(lot(LotStatus.DELIVERED, FoodCategory.BAKERY));
        given(deliveryRepository.findByLotIdWithLot(LOT_ID)).willReturn(Optional.of(delivery));
        ConfirmationRequest invalid = new ConfirmationRequest("000000", new BigDecimal("10.00"));
        ConfirmationRequest correct = new ConfirmationRequest("123456", new BigDecimal("10.00"));

        for (int i = 0; i < 4; i++) {
            assertThrows(InvalidConfirmationCodeException.class, () -> service.confirm(LOT_ID, invalid));
        }
        assertThrows(ConfirmationAttemptsExceededException.class, () -> service.confirm(LOT_ID, invalid));
        assertEquals(5, delivery.getFailedConfirmationAttempts());
        assertThrows(ConfirmationAttemptsExceededException.class, () -> service.confirm(LOT_ID, correct));

        verify(deliveryRepository, times(5)).save(delivery);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void deliver_generatesSixDigitConfirmationCode() {
        asVolunteer(VOLUNTEER_ID);
        FoodLot lot = lot(LotStatus.PICKED_UP, FoodCategory.BAKERY);
        Delivery delivery = delivery(lot);
        given(deliveryRepository.findByLotIdWithLot(LOT_ID)).willReturn(Optional.of(delivery));
        given(destinationPointRepository.findByIdWithCategories(POINT_ID))
                .willReturn(Optional.of(point(Set.of(FoodCategory.BAKERY))));
        doAnswer(invocation -> {
            lot.setStatus(LotStatus.DELIVERED);
            return null;
        }).when(statusChanger).transition(lot, LotStatus.DELIVERED, "Доставлено до пункту призначення");

        service.deliver(LOT_ID, new DeliveryRequest(POINT_ID));

        org.junit.jupiter.api.Assertions.assertTrue(delivery.getConfirmationCode().matches("\\d{6}"));
    }
}
