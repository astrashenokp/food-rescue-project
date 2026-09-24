package com.example.foodrescue.delivery;

import com.example.foodrescue.common.FoodCategory;
import com.example.foodrescue.common.FoodLot;
import com.example.foodrescue.common.InvalidLotStateException;
import com.example.foodrescue.common.LotNotFoundException;
import com.example.foodrescue.common.LotRepository;
import com.example.foodrescue.common.LotStatus;
import com.example.foodrescue.common.LotStatusChanger;
import com.example.foodrescue.common.LotStatusHistory;
import com.example.foodrescue.common.StatusHistoryRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class DeliveryServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-09-20T12:00:00Z");
    private static final UUID LOT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID DONOR_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final UUID VOLUNTEER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID POINT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

    @Mock
    private LotRepository lotRepository;

    @Mock
    private LotStatusChanger statusChanger;

    @Mock
    private StatusHistoryRecorder historyRecorder;

    @Mock
    private DestinationPointRepository destinationPointRepository;

    @Mock
    private DeliveryRepository deliveryRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private DeliveryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DeliveryServiceImpl(
                lotRepository,
                statusChanger,
                historyRecorder,
                destinationPointRepository,
                deliveryRepository,
                List.of(
                        new PreparedMealTolerance(),
                        new BakeryTolerance(),
                        new VegetablesTolerance(),
                        new GroceryTolerance()),
                eventPublisher,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private FoodLot lot(LotStatus status, FoodCategory category) {
        FoodLot lot = new FoodLot(LOT_ID);
        lot.setDonorOrgId(DONOR_ID);
        lot.setReservedByVolunteerId(VOLUNTEER_ID);
        lot.setReservedUntil(NOW.plus(30, ChronoUnit.MINUTES));
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

    private Delivery pickedUpDelivery(BigDecimal pickupWeight, boolean latePickup) {
        return new Delivery(
                LOT_ID,
                VOLUNTEER_ID,
                null,
                NOW.minus(20, ChronoUnit.MINUTES),
                pickupWeight,
                latePickup,
                null,
                null,
                null,
                null);
    }

    private Delivery deliveredDelivery(BigDecimal pickupWeight, boolean latePickup) {
        return new Delivery(
                LOT_ID,
                VOLUNTEER_ID,
                POINT_ID,
                NOW.minus(30, ChronoUnit.MINUTES),
                pickupWeight,
                latePickup,
                NOW.minus(10, ChronoUnit.MINUTES),
                "123456",
                null,
                null);
    }

    private void makeTransitionSetStatus(FoodLot lot, LotStatus target, String comment) {
        doAnswer(invocation -> {
            lot.setStatus(target);
            return null;
        }).when(statusChanger).transition(lot, target, comment);
    }

    // --- createDestinationPoint ---

    @Test
    void createDestinationPoint_uniqueName_savesPoint() {
        DestinationPointRequest request = new DestinationPointRequest(
                UUID.randomUUID(),
                "Благодійний центр",
                "вул. Центральна, 10",
                "09:00-18:00",
                Set.of(FoodCategory.BAKERY));
        given(destinationPointRepository.existsByName(request.name())).willReturn(false);
        given(destinationPointRepository.save(any(DestinationPoint.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        DestinationPoint created = service.createDestinationPoint(request);

        assertNotNull(created.getId());
        assertEquals(request.name(), created.getName());
        verify(destinationPointRepository).existsByName(request.name());
        verify(destinationPointRepository).save(created);
        verifyNoMoreInteractions(destinationPointRepository);
        verifyNoInteractions(lotRepository, statusChanger, historyRecorder, deliveryRepository, eventPublisher);
    }

    @Test
    void createDestinationPoint_duplicateName_throwsAndDoesNotSave() {
        DestinationPointRequest request = new DestinationPointRequest(
                UUID.randomUUID(),
                "Благодійний центр",
                "вул. Центральна, 10",
                "09:00-18:00",
                Set.of(FoodCategory.BAKERY));
        given(destinationPointRepository.existsByName(request.name())).willReturn(true);

        assertThrows(DuplicateDestinationPointException.class,
                () -> service.createDestinationPoint(request));

        verify(destinationPointRepository).existsByName(request.name());
        verify(destinationPointRepository, never()).save(any());
        verifyNoMoreInteractions(destinationPointRepository);
        verifyNoInteractions(lotRepository, statusChanger, historyRecorder, deliveryRepository, eventPublisher);
    }

    // --- getDestinationPoint ---

    @Test
    void getDestinationPoint_existingPoint_returnsIt() {
        DestinationPoint point = point(Set.of(FoodCategory.BAKERY));
        given(destinationPointRepository.findById(POINT_ID)).willReturn(Optional.of(point));

        assertSame(point, service.getDestinationPoint(POINT_ID));

        verify(destinationPointRepository).findById(POINT_ID);
        verifyNoMoreInteractions(destinationPointRepository);
        verifyNoInteractions(lotRepository, statusChanger, historyRecorder, deliveryRepository, eventPublisher);
    }

    @Test
    void getDestinationPoint_unknownPoint_throwsNotFound() {
        given(destinationPointRepository.findById(POINT_ID)).willReturn(Optional.empty());

        assertThrows(DestinationPointNotFoundException.class,
                () -> service.getDestinationPoint(POINT_ID));

        verify(destinationPointRepository).findById(POINT_ID);
        verifyNoMoreInteractions(destinationPointRepository);
        verifyNoInteractions(lotRepository, statusChanger, historyRecorder, deliveryRepository, eventPublisher);
    }

    // --- pickup ---

    @Test
    void pickup_reservedLot_savesDeliveryAndLatePickupFlag() {
        FoodLot lot = lot(LotStatus.RESERVED, FoodCategory.BAKERY);
        lot.setReservedUntil(NOW.minus(1, ChronoUnit.MINUTES));
        given(lotRepository.findById(LOT_ID)).willReturn(Optional.of(lot));
        makeTransitionSetStatus(lot, LotStatus.PICKED_UP, "Лот передано волонтеру");
        ArgumentCaptor<Delivery> deliveryCaptor = ArgumentCaptor.forClass(Delivery.class);

        DeliveryResponse response = service.pickup(LOT_ID, new PickupRequest(new BigDecimal("12.5")));

        assertEquals(LotStatus.PICKED_UP, response.lotStatus());
        assertEquals(NOW, response.pickedUpAt());
        verify(statusChanger).transition(lot, LotStatus.PICKED_UP, "Лот передано волонтеру");
        verify(deliveryRepository).save(deliveryCaptor.capture());
        assertTrue(deliveryCaptor.getValue().latePickup());
        assertEquals(new BigDecimal("12.5"), deliveryCaptor.getValue().pickupWeightKg());
        verify(lotRepository).findById(LOT_ID);
        verify(lotRepository).save(lot);
        verifyNoMoreInteractions(lotRepository, statusChanger, deliveryRepository);
        verifyNoInteractions(historyRecorder, destinationPointRepository, eventPublisher);
    }

    @Test
    void pickup_lotNotFound_throwsAndDoesNotSave() {
        given(lotRepository.findById(LOT_ID)).willReturn(Optional.empty());

        assertThrows(LotNotFoundException.class,
                () -> service.pickup(LOT_ID, new PickupRequest(new BigDecimal("10"))));

        verify(lotRepository).findById(LOT_ID);
        verify(lotRepository, never()).save(any());
        verify(deliveryRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
        verifyNoMoreInteractions(lotRepository, deliveryRepository, eventPublisher);
        verifyNoInteractions(statusChanger, historyRecorder, destinationPointRepository);
    }

    @Test
    void pickup_invalidTransition_doesNotSaveOrPublish() {
        FoodLot lot = lot(LotStatus.PUBLISHED, FoodCategory.BAKERY);
        given(lotRepository.findById(LOT_ID)).willReturn(Optional.of(lot));
        doThrow(new InvalidLotStateException("перехід заборонено"))
                .when(statusChanger).transition(lot, LotStatus.PICKED_UP, "Лот передано волонтеру");

        assertThrows(InvalidLotStateException.class,
                () -> service.pickup(LOT_ID, new PickupRequest(new BigDecimal("10"))));

        verify(lotRepository).findById(LOT_ID);
        verify(statusChanger).transition(lot, LotStatus.PICKED_UP, "Лот передано волонтеру");
        verify(lotRepository, never()).save(any());
        verify(deliveryRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
        verifyNoMoreInteractions(lotRepository, statusChanger, deliveryRepository, eventPublisher);
        verifyNoInteractions(historyRecorder, destinationPointRepository);
    }

    // --- deliver ---

    @Test
    void deliver_acceptedCategory_savesDeliveryWithSixDigitCode() {
        FoodLot lot = lot(LotStatus.PICKED_UP, FoodCategory.BAKERY);
        DestinationPoint point = point(Set.of(FoodCategory.BAKERY, FoodCategory.GROCERY));
        Delivery current = pickedUpDelivery(new BigDecimal("10"), false);
        given(lotRepository.findById(LOT_ID)).willReturn(Optional.of(lot));
        given(destinationPointRepository.findById(POINT_ID)).willReturn(Optional.of(point));
        given(deliveryRepository.findByLotId(LOT_ID)).willReturn(Optional.of(current));
        makeTransitionSetStatus(lot, LotStatus.DELIVERED, "Доставлено до пункту призначення");
        ArgumentCaptor<Delivery> deliveryCaptor = ArgumentCaptor.forClass(Delivery.class);

        DeliveryResponse response = service.deliver(LOT_ID, new DeliveryRequest(POINT_ID));

        assertEquals(LotStatus.DELIVERED, response.lotStatus());
        assertEquals(NOW, response.deliveredAt());
        assertNotNull(response.confirmationCode());
        assertTrue(response.confirmationCode().matches("\\d{6}"));
        verify(lotRepository).findById(LOT_ID);
        verify(destinationPointRepository).findById(POINT_ID);
        verify(deliveryRepository).findByLotId(LOT_ID);
        verify(statusChanger).transition(lot, LotStatus.DELIVERED, "Доставлено до пункту призначення");
        verify(deliveryRepository).save(deliveryCaptor.capture());
        assertEquals(POINT_ID, deliveryCaptor.getValue().destinationPointId());
        assertFalse(deliveryCaptor.getValue().latePickup());
        verify(lotRepository).save(lot);
        verifyNoMoreInteractions(lotRepository, statusChanger, destinationPointRepository, deliveryRepository);
        verifyNoInteractions(historyRecorder, eventPublisher);
    }

    @Test
    void deliver_destinationPointNotFound_throwsAndDoesNotSave() {
        FoodLot lot = lot(LotStatus.PICKED_UP, FoodCategory.BAKERY);
        given(lotRepository.findById(LOT_ID)).willReturn(Optional.of(lot));
        given(destinationPointRepository.findById(POINT_ID)).willReturn(Optional.empty());

        assertThrows(DestinationPointNotFoundException.class,
                () -> service.deliver(LOT_ID, new DeliveryRequest(POINT_ID)));

        verify(lotRepository).findById(LOT_ID);
        verify(destinationPointRepository).findById(POINT_ID);
        verify(lotRepository, never()).save(any());
        verify(deliveryRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
        verifyNoMoreInteractions(lotRepository, destinationPointRepository, deliveryRepository, eventPublisher);
        verifyNoInteractions(statusChanger, historyRecorder);
    }

    @Test
    void deliver_categoryNotAccepted_throwsAndDoesNotSave() {
        FoodLot lot = lot(LotStatus.PICKED_UP, FoodCategory.VEGETABLES);
        DestinationPoint point = point(Set.of(FoodCategory.BAKERY));
        given(lotRepository.findById(LOT_ID)).willReturn(Optional.of(lot));
        given(destinationPointRepository.findById(POINT_ID)).willReturn(Optional.of(point));

        assertThrows(CategoryNotAcceptedException.class,
                () -> service.deliver(LOT_ID, new DeliveryRequest(POINT_ID)));

        verify(lotRepository).findById(LOT_ID);
        verify(destinationPointRepository).findById(POINT_ID);
        verify(lotRepository, never()).save(any());
        verify(deliveryRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
        verifyNoMoreInteractions(lotRepository, destinationPointRepository, deliveryRepository, eventPublisher);
        verifyNoInteractions(statusChanger, historyRecorder);
    }

    @Test
    void deliver_deliveryNotFound_throwsAndDoesNotSave() {
        FoodLot lot = lot(LotStatus.PICKED_UP, FoodCategory.BAKERY);
        DestinationPoint point = point(Set.of(FoodCategory.BAKERY));
        given(lotRepository.findById(LOT_ID)).willReturn(Optional.of(lot));
        given(destinationPointRepository.findById(POINT_ID)).willReturn(Optional.of(point));
        given(deliveryRepository.findByLotId(LOT_ID)).willReturn(Optional.empty());

        assertThrows(DeliveryNotFoundException.class,
                () -> service.deliver(LOT_ID, new DeliveryRequest(POINT_ID)));

        verify(lotRepository).findById(LOT_ID);
        verify(destinationPointRepository).findById(POINT_ID);
        verify(deliveryRepository).findByLotId(LOT_ID);
        verify(lotRepository, never()).save(any());
        verify(deliveryRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
        verifyNoMoreInteractions(lotRepository, destinationPointRepository, deliveryRepository, eventPublisher);
        verifyNoInteractions(statusChanger, historyRecorder);
    }

    @Test
    void deliver_invalidTransition_doesNotSaveOrPublish() {
        FoodLot lot = lot(LotStatus.RESERVED, FoodCategory.BAKERY);
        DestinationPoint point = point(Set.of(FoodCategory.BAKERY));
        Delivery current = pickedUpDelivery(new BigDecimal("10"), true);
        given(lotRepository.findById(LOT_ID)).willReturn(Optional.of(lot));
        given(destinationPointRepository.findById(POINT_ID)).willReturn(Optional.of(point));
        given(deliveryRepository.findByLotId(LOT_ID)).willReturn(Optional.of(current));
        doThrow(new InvalidLotStateException("перехід заборонено"))
                .when(statusChanger).transition(lot, LotStatus.DELIVERED, "Доставлено до пункту призначення");

        assertThrows(InvalidLotStateException.class,
                () -> service.deliver(LOT_ID, new DeliveryRequest(POINT_ID)));

        verify(lotRepository).findById(LOT_ID);
        verify(destinationPointRepository).findById(POINT_ID);
        verify(deliveryRepository).findByLotId(LOT_ID);
        verify(statusChanger).transition(lot, LotStatus.DELIVERED, "Доставлено до пункту призначення");
        verify(lotRepository, never()).save(any());
        verify(deliveryRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
        verifyNoMoreInteractions(lotRepository, statusChanger, destinationPointRepository, deliveryRepository, eventPublisher);
        verifyNoInteractions(historyRecorder);
    }

    // --- confirm ---

    @Test
    void confirm_groceryDifferenceAboveFivePercent_marksDisputedAndPublishesEvent() {
        FoodLot lot = lot(LotStatus.DELIVERED, FoodCategory.GROCERY);
        Delivery current = deliveredDelivery(new BigDecimal("100"), true);
        given(lotRepository.findById(LOT_ID)).willReturn(Optional.of(lot));
        given(deliveryRepository.findByLotId(LOT_ID)).willReturn(Optional.of(current));
        makeTransitionSetStatus(lot, LotStatus.DISPUTED, "Виявлено розбіжність у вазі");
        ArgumentCaptor<DeliveryFinishedEvent> eventCaptor = ArgumentCaptor.forClass(DeliveryFinishedEvent.class);

        DeliveryResponse response = service.confirm(
                LOT_ID,
                new ConfirmationRequest("123456", new BigDecimal("94")));

        assertEquals(LotStatus.DISPUTED, response.lotStatus());
        assertEquals(NOW, response.confirmedAt());
        verify(lotRepository).findById(LOT_ID);
        verify(deliveryRepository).findByLotId(LOT_ID);
        verify(statusChanger).transition(lot, LotStatus.DISPUTED, "Виявлено розбіжність у вазі");
        verify(deliveryRepository).save(any(Delivery.class));
        verify(lotRepository).save(lot);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        DeliveryFinishedEvent event = eventCaptor.getValue();
        assertEquals(LOT_ID, event.lotId());
        assertEquals(DONOR_ID, event.donorOrgId());
        assertEquals(VOLUNTEER_ID, event.volunteerId());
        assertEquals(DeliveryOutcome.DISPUTED, event.outcome());
        assertTrue(event.latePickup());
        verifyNoMoreInteractions(lotRepository, statusChanger, deliveryRepository, eventPublisher);
        verifyNoInteractions(historyRecorder, destinationPointRepository);
    }

    @Test
    void confirm_differenceExactlyAtBakeryTolerance_marksConfirmed() {
        FoodLot lot = lot(LotStatus.DELIVERED, FoodCategory.BAKERY);
        Delivery current = deliveredDelivery(new BigDecimal("100"), false);
        given(lotRepository.findById(LOT_ID)).willReturn(Optional.of(lot));
        given(deliveryRepository.findByLotId(LOT_ID)).willReturn(Optional.of(current));
        makeTransitionSetStatus(lot, LotStatus.CONFIRMED, "Отримання підтверджено");
        ArgumentCaptor<DeliveryFinishedEvent> eventCaptor = ArgumentCaptor.forClass(DeliveryFinishedEvent.class);

        DeliveryResponse response = service.confirm(
                LOT_ID,
                new ConfirmationRequest("123456", new BigDecimal("90")));

        assertEquals(LotStatus.CONFIRMED, response.lotStatus());
        verify(statusChanger).transition(lot, LotStatus.CONFIRMED, "Отримання підтверджено");
        verify(deliveryRepository).save(any(Delivery.class));
        verify(lotRepository).findById(LOT_ID);
        verify(lotRepository).save(lot);
        verify(deliveryRepository).findByLotId(LOT_ID);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals(DeliveryOutcome.CONFIRMED, eventCaptor.getValue().outcome());
        assertFalse(eventCaptor.getValue().latePickup());
        verifyNoMoreInteractions(lotRepository, statusChanger, deliveryRepository, eventPublisher);
        verifyNoInteractions(historyRecorder, destinationPointRepository);
    }

    @Test
    void confirm_invalidCode_throwsAndDoesNotSaveOrPublish() {
        FoodLot lot = lot(LotStatus.DELIVERED, FoodCategory.BAKERY);
        Delivery current = deliveredDelivery(new BigDecimal("100"), false);
        given(lotRepository.findById(LOT_ID)).willReturn(Optional.of(lot));
        given(deliveryRepository.findByLotId(LOT_ID)).willReturn(Optional.of(current));

        assertThrows(InvalidConfirmationCodeException.class,
                () -> service.confirm(LOT_ID, new ConfirmationRequest("654321", new BigDecimal("100"))));

        verify(lotRepository).findById(LOT_ID);
        verify(deliveryRepository).findByLotId(LOT_ID);
        verify(lotRepository, never()).save(any());
        verify(deliveryRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
        verifyNoMoreInteractions(lotRepository, deliveryRepository, eventPublisher);
        verifyNoInteractions(statusChanger, historyRecorder, destinationPointRepository);
    }

    @Test
    void confirm_lotNotFound_throwsAndDoesNotPublish() {
        given(lotRepository.findById(LOT_ID)).willReturn(Optional.empty());

        assertThrows(LotNotFoundException.class,
                () -> service.confirm(LOT_ID, new ConfirmationRequest("123456", new BigDecimal("100"))));

        verify(lotRepository).findById(LOT_ID);
        verify(lotRepository, never()).save(any());
        verify(deliveryRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
        verifyNoMoreInteractions(lotRepository, deliveryRepository, eventPublisher);
        verifyNoInteractions(statusChanger, historyRecorder, destinationPointRepository);
    }

    @Test
    void confirm_saveFails_doesNotPublishEvent() {
        FoodLot lot = lot(LotStatus.DELIVERED, FoodCategory.BAKERY);
        Delivery current = deliveredDelivery(new BigDecimal("100"), false);
        given(lotRepository.findById(LOT_ID)).willReturn(Optional.of(lot));
        given(deliveryRepository.findByLotId(LOT_ID)).willReturn(Optional.of(current));
        makeTransitionSetStatus(lot, LotStatus.CONFIRMED, "Отримання підтверджено");
        given(lotRepository.save(lot)).willThrow(new IllegalStateException("save failed"));

        assertThrows(IllegalStateException.class,
                () -> service.confirm(LOT_ID, new ConfirmationRequest("123456", new BigDecimal("100"))));

        verify(lotRepository).findById(LOT_ID);
        verify(deliveryRepository).findByLotId(LOT_ID);
        verify(statusChanger).transition(lot, LotStatus.CONFIRMED, "Отримання підтверджено");
        verify(deliveryRepository).save(any(Delivery.class));
        verify(lotRepository).save(lot);
        verify(eventPublisher, never()).publishEvent(any());
        verifyNoMoreInteractions(lotRepository, statusChanger, deliveryRepository, eventPublisher);
        verifyNoInteractions(historyRecorder, destinationPointRepository);
    }

    @Test
    void confirm_deliveryNotFound_throwsAndDoesNotPublish() {
        FoodLot lot = lot(LotStatus.DELIVERED, FoodCategory.BAKERY);
        given(lotRepository.findById(LOT_ID)).willReturn(Optional.of(lot));
        given(deliveryRepository.findByLotId(LOT_ID)).willReturn(Optional.empty());

        assertThrows(DeliveryNotFoundException.class,
                () -> service.confirm(LOT_ID, new ConfirmationRequest("123456", new BigDecimal("100"))));

        verify(lotRepository).findById(LOT_ID);
        verify(deliveryRepository).findByLotId(LOT_ID);
        verify(lotRepository, never()).save(any());
        verify(deliveryRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
        verifyNoMoreInteractions(lotRepository, deliveryRepository, eventPublisher);
        verifyNoInteractions(statusChanger, historyRecorder, destinationPointRepository);
    }

    @Test
    void confirm_invalidTransition_doesNotSaveOrPublish() {
        FoodLot lot = lot(LotStatus.PICKED_UP, FoodCategory.VEGETABLES);
        Delivery current = deliveredDelivery(new BigDecimal("100"), false);
        given(lotRepository.findById(LOT_ID)).willReturn(Optional.of(lot));
        given(deliveryRepository.findByLotId(LOT_ID)).willReturn(Optional.of(current));
        doThrow(new InvalidLotStateException("перехід заборонено"))
                .when(statusChanger).transition(lot, LotStatus.CONFIRMED, "Отримання підтверджено");

        assertThrows(InvalidLotStateException.class,
                () -> service.confirm(LOT_ID, new ConfirmationRequest("123456", new BigDecimal("86"))));

        verify(lotRepository).findById(LOT_ID);
        verify(deliveryRepository).findByLotId(LOT_ID);
        verify(statusChanger).transition(lot, LotStatus.CONFIRMED, "Отримання підтверджено");
        verify(lotRepository, never()).save(any());
        verify(deliveryRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
        verifyNoMoreInteractions(lotRepository, statusChanger, deliveryRepository, eventPublisher);
        verifyNoInteractions(historyRecorder, destinationPointRepository);
    }

    // --- getHistory ---

    @Test
    void getHistory_existingLot_returnsMappedHistory() {
        FoodLot lot = lot(LotStatus.DELIVERED, FoodCategory.BAKERY);
        LotStatusHistory history = new LotStatusHistory(
                LOT_ID,
                LotStatus.PICKED_UP,
                LotStatus.DELIVERED,
                NOW.minus(10, ChronoUnit.MINUTES),
                "Доставлено до пункту призначення");
        given(lotRepository.findById(LOT_ID)).willReturn(Optional.of(lot));
        given(historyRecorder.findByLot(LOT_ID)).willReturn(List.of(history));

        List<StatusHistoryResponse> result = service.getHistory(LOT_ID);

        assertEquals(1, result.size());
        assertEquals(LotStatus.PICKED_UP, result.getFirst().fromStatus());
        assertEquals(LotStatus.DELIVERED, result.getFirst().toStatus());
        verify(lotRepository).findById(LOT_ID);
        verify(historyRecorder).findByLot(LOT_ID);
        verifyNoMoreInteractions(lotRepository, historyRecorder);
        verifyNoInteractions(statusChanger, destinationPointRepository, deliveryRepository, eventPublisher);
    }

    @Test
    void getHistory_lotNotFound_throwsAndDoesNotReadHistory() {
        given(lotRepository.findById(LOT_ID)).willReturn(Optional.empty());

        assertThrows(LotNotFoundException.class, () -> service.getHistory(LOT_ID));

        verify(lotRepository).findById(LOT_ID);
        verifyNoMoreInteractions(lotRepository);
        verifyNoInteractions(statusChanger, historyRecorder, destinationPointRepository, deliveryRepository, eventPublisher);
    }
}
