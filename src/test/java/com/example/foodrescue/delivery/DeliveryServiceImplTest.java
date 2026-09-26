package com.example.foodrescue.delivery;

import com.example.foodrescue.common.FoodCategory;
import com.example.foodrescue.common.FoodLot;
import com.example.foodrescue.common.LotRepository;
import com.example.foodrescue.common.LotStatus;
import com.example.foodrescue.common.LotStatusChanger;
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
                Clock.fixed(NOW, ZoneOffset.UTC));
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
        FoodLot lot = lot(LotStatus.DELIVERED, FoodCategory.BAKERY);
        given(deliveryRepository.findAllWithLotAndPoint()).willReturn(List.of(delivery(lot)));

        List<DeliveryResponse> result = service.getDeliveries();

        assertEquals(1, result.size());
        verify(deliveryRepository).findAllWithLotAndPoint();
        verifyNoMoreInteractions(deliveryRepository);
    }
}
