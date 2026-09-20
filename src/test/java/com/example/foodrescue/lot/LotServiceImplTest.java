package com.example.foodrescue.lot;

import com.example.foodrescue.common.FoodCategory;
import com.example.foodrescue.common.FoodLot;
import com.example.foodrescue.common.InvalidLotStateException;
import com.example.foodrescue.common.ItemUnit;
import com.example.foodrescue.common.LotNotFoundException;
import com.example.foodrescue.common.LotRepository;
import com.example.foodrescue.common.LotStatus;
import com.example.foodrescue.common.LotStatusChanger;
import com.example.foodrescue.common.StorageCondition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class LotServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-09-20T12:00:00Z");
    private static final UUID DONOR = UUID.fromString("00000000-0000-0000-0000-00000000d001");
    private static final UUID OTHER_DONOR = UUID.fromString("00000000-0000-0000-0000-00000000d002");

    @Mock
    private LotRepository lotRepository;

    @Mock
    private LotStatusChanger statusChanger;

    private LotServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LotServiceImpl(
                lotRepository,
                statusChanger,
                List.of(new PreparedMealPickupWindow(), new BakeryPickupWindow(),
                        new VegetablesPickupWindow(), new GroceryPickupWindow()),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private LotRequest request(FoodCategory category, Instant from, Instant to) {
        return new LotRequest(
                DONOR,
                "Хліб і випічка",
                category,
                List.of(new FoodItemRequest("Хліб", BigDecimal.valueOf(5), ItemUnit.KG, null)),
                BigDecimal.valueOf(5),
                StorageCondition.ROOM,
                "вул. Хлібна, 1",
                from,
                to);
    }

    private LotRequest validRequest() {
        Instant from = NOW.plus(2, ChronoUnit.HOURS);
        return request(FoodCategory.BAKERY, from, from.plus(2, ChronoUnit.HOURS));
    }

    private FoodLot lot(LotStatus status) {
        FoodLot lot = new FoodLot();
        lot.setId(UUID.randomUUID());
        lot.setDonorOrgId(DONOR);
        lot.setCategory(FoodCategory.BAKERY);
        lot.setTitle("Старий заголовок");
        lot.setCreatedAt(NOW.minus(1, ChronoUnit.DAYS));
        lot.setStatus(status);
        return lot;
    }

    private FoodLot otherLot(UUID donor, FoodCategory category, LotStatus status) {
        FoodLot lot = new FoodLot();
        lot.setId(UUID.randomUUID());
        lot.setDonorOrgId(donor);
        lot.setCategory(category);
        lot.setStatus(status);
        return lot;
    }

    private List<FoodLot> donorLots(FoodLot target, int otherLotsCount, int cancelledCount) {
        List<FoodLot> lots = new ArrayList<>();
        lots.add(target);
        for (int i = 0; i < otherLotsCount; i++) {
            lots.add(otherLot(DONOR, FoodCategory.BAKERY, i < cancelledCount ? LotStatus.CANCELLED : LotStatus.PUBLISHED));
        }
        return lots;
    }

    @Test
    void create_validRequest_savesDraftLot() {
        given(lotRepository.save(any(FoodLot.class))).willAnswer(invocation -> invocation.getArgument(0));

        FoodLot created = service.create(validRequest());

        assertNotNull(created.getId());
        assertEquals(LotStatus.DRAFT, created.getStatus());
        assertEquals(NOW, created.getCreatedAt());
        assertNull(created.getPublishedAt());
        assertEquals("Хліб і випічка", created.getTitle());
        assertEquals(FoodCategory.BAKERY, created.getCategory());
        assertEquals(1, created.getItems().size());
        verify(lotRepository).save(created);
        verifyNoMoreInteractions(lotRepository);
        verifyNoInteractions(statusChanger);
    }

    @Test
    void create_pickupToNotAfterPickupFrom_throwsAndDoesNotSave() {
        Instant from = NOW.plus(2, ChronoUnit.HOURS);

        assertThrows(InvalidPickupWindowException.class,
                () -> service.create(request(FoodCategory.BAKERY, from, from)));

        verify(lotRepository, never()).save(any());
        verifyNoMoreInteractions(lotRepository);
        verifyNoInteractions(statusChanger);
    }

    @Test
    void create_pickupFromNotInFuture_throwsAndDoesNotSave() {
        assertThrows(InvalidPickupWindowException.class,
                () -> service.create(request(FoodCategory.BAKERY, NOW, NOW.plus(3, ChronoUnit.HOURS))));

        verify(lotRepository, never()).save(any());
        verifyNoMoreInteractions(lotRepository);
        verifyNoInteractions(statusChanger);
    }

    @ParameterizedTest
    @CsvSource({"PREPARED_MEAL,45", "BAKERY,60", "VEGETABLES,90", "GROCERY,120"})
    void create_windowShorterThanCategoryMinimum_throwsAndDoesNotSave(FoodCategory category, int minMinutes) {
        Instant from = NOW.plus(1, ChronoUnit.HOURS);

        assertThrows(InvalidPickupWindowException.class,
                () -> service.create(request(category, from, from.plus(minMinutes - 1, ChronoUnit.MINUTES))));

        verify(lotRepository, never()).save(any());
        verifyNoMoreInteractions(lotRepository);
        verifyNoInteractions(statusChanger);
    }

    @ParameterizedTest
    @CsvSource({"PREPARED_MEAL,45", "BAKERY,60", "VEGETABLES,90", "GROCERY,120"})
    void create_windowEqualToCategoryMinimum_isAccepted(FoodCategory category, int minMinutes) {
        given(lotRepository.save(any(FoodLot.class))).willAnswer(invocation -> invocation.getArgument(0));
        Instant from = NOW.plus(1, ChronoUnit.HOURS);

        FoodLot created = service.create(request(category, from, from.plus(minMinutes, ChronoUnit.MINUTES)));

        assertEquals(category, created.getCategory());
        verify(lotRepository).save(created);
        verifyNoMoreInteractions(lotRepository);
        verifyNoInteractions(statusChanger);
    }

    @Test
    void update_draftLot_appliesRequestAndKeepsStatus() {
        FoodLot lot = lot(LotStatus.DRAFT);
        given(lotRepository.findById(lot.getId())).willReturn(Optional.of(lot));
        given(lotRepository.save(lot)).willReturn(lot);

        FoodLot updated = service.update(lot.getId(), validRequest());

        assertSame(lot, updated);
        assertEquals("Хліб і випічка", updated.getTitle());
        assertEquals(LotStatus.DRAFT, updated.getStatus());
        assertEquals(NOW.minus(1, ChronoUnit.DAYS), updated.getCreatedAt());
        verify(lotRepository).findById(lot.getId());
        verify(lotRepository).save(lot);
        verifyNoMoreInteractions(lotRepository);
        verifyNoInteractions(statusChanger);
    }

    @Test
    void update_lotNotDraft_throwsLotNotDraftAndDoesNotSave() {
        FoodLot lot = lot(LotStatus.PUBLISHED);
        given(lotRepository.findById(lot.getId())).willReturn(Optional.of(lot));

        assertThrows(LotNotDraftException.class, () -> service.update(lot.getId(), validRequest()));

        verify(lotRepository).findById(lot.getId());
        verify(lotRepository, never()).save(any());
        verifyNoMoreInteractions(lotRepository);
        verifyNoInteractions(statusChanger);
    }

    @Test
    void update_unknownLot_throwsLotNotFound() {
        UUID id = UUID.randomUUID();
        given(lotRepository.findById(id)).willReturn(Optional.empty());

        assertThrows(LotNotFoundException.class, () -> service.update(id, validRequest()));

        verify(lotRepository).findById(id);
        verifyNoMoreInteractions(lotRepository);
        verifyNoInteractions(statusChanger);
    }

    @Test
    void update_invalidPickupWindow_throwsAndDoesNotSave() {
        FoodLot lot = lot(LotStatus.DRAFT);
        given(lotRepository.findById(lot.getId())).willReturn(Optional.of(lot));
        Instant from = NOW.plus(2, ChronoUnit.HOURS);

        assertThrows(InvalidPickupWindowException.class,
                () -> service.update(lot.getId(), request(FoodCategory.BAKERY, from, from.plus(10, ChronoUnit.MINUTES))));

        assertEquals("Старий заголовок", lot.getTitle());
        verify(lotRepository).findById(lot.getId());
        verify(lotRepository, never()).save(any());
        verifyNoMoreInteractions(lotRepository);
        verifyNoInteractions(statusChanger);
    }

    @Test
    void publish_regularDonor_publishesAndSetsPublishedAt() {
        FoodLot lot = lot(LotStatus.DRAFT);
        given(lotRepository.findById(lot.getId())).willReturn(Optional.of(lot));
        given(lotRepository.findAll()).willReturn(List.of(lot));
        given(lotRepository.save(lot)).willReturn(lot);

        FoodLot published = service.publish(lot.getId());

        assertEquals(NOW, published.getPublishedAt());
        verify(statusChanger).transition(lot, LotStatus.PUBLISHED, "Публікація лоту");
        verify(lotRepository).findById(lot.getId());
        verify(lotRepository).findAll();
        verify(lotRepository).save(lot);
        verifyNoMoreInteractions(lotRepository, statusChanger);
    }

    @Test
    void publish_donorWithMoreThan20PercentCancelled_goesToModeration() {
        FoodLot lot = lot(LotStatus.DRAFT);
        given(lotRepository.findById(lot.getId())).willReturn(Optional.of(lot));
        given(lotRepository.findAll()).willReturn(donorLots(lot, 4, 2));
        given(lotRepository.save(lot)).willReturn(lot);

        FoodLot result = service.publish(lot.getId());

        assertNull(result.getPublishedAt());
        verify(statusChanger).transition(lot, LotStatus.PENDING_MODERATION, "Публікація лоту");
        verify(lotRepository).findById(lot.getId());
        verify(lotRepository).findAll();
        verify(lotRepository).save(lot);
        verifyNoMoreInteractions(lotRepository, statusChanger);
    }

    @Test
    void publish_donorWithExactly20PercentCancelled_isPublished() {
        FoodLot lot = lot(LotStatus.DRAFT);
        given(lotRepository.findById(lot.getId())).willReturn(Optional.of(lot));
        given(lotRepository.findAll()).willReturn(donorLots(lot, 4, 1));
        given(lotRepository.save(lot)).willReturn(lot);

        FoodLot result = service.publish(lot.getId());

        assertEquals(NOW, result.getPublishedAt());
        verify(statusChanger).transition(lot, LotStatus.PUBLISHED, "Публікація лоту");
        verify(lotRepository).findById(lot.getId());
        verify(lotRepository).findAll();
        verify(lotRepository).save(lot);
        verifyNoMoreInteractions(lotRepository, statusChanger);
    }

    @Test
    void publish_donorWithFewerThanFiveLots_ignoresCancellations() {
        FoodLot lot = lot(LotStatus.DRAFT);
        given(lotRepository.findById(lot.getId())).willReturn(Optional.of(lot));
        given(lotRepository.findAll()).willReturn(donorLots(lot, 3, 3));
        given(lotRepository.save(lot)).willReturn(lot);

        FoodLot result = service.publish(lot.getId());

        assertEquals(NOW, result.getPublishedAt());
        verify(statusChanger).transition(lot, LotStatus.PUBLISHED, "Публікація лоту");
        verify(lotRepository).findById(lot.getId());
        verify(lotRepository).findAll();
        verify(lotRepository).save(lot);
        verifyNoMoreInteractions(lotRepository, statusChanger);
    }

    @Test
    void publish_countsOnlyLotsOfSameDonor() {
        FoodLot lot = lot(LotStatus.DRAFT);
        List<FoodLot> lots = donorLots(lot, 4, 1);
        lots.add(otherLot(OTHER_DONOR, FoodCategory.BAKERY, LotStatus.CANCELLED));
        lots.add(otherLot(OTHER_DONOR, FoodCategory.BAKERY, LotStatus.CANCELLED));
        lots.add(otherLot(OTHER_DONOR, FoodCategory.BAKERY, LotStatus.CANCELLED));
        given(lotRepository.findById(lot.getId())).willReturn(Optional.of(lot));
        given(lotRepository.findAll()).willReturn(lots);
        given(lotRepository.save(lot)).willReturn(lot);

        service.publish(lot.getId());

        verify(statusChanger).transition(lot, LotStatus.PUBLISHED, "Публікація лоту");
        verify(lotRepository).findById(lot.getId());
        verify(lotRepository).findAll();
        verify(lotRepository).save(lot);
        verifyNoMoreInteractions(lotRepository, statusChanger);
    }

    @Test
    void publish_invalidTransition_doesNotSave() {
        FoodLot lot = lot(LotStatus.PUBLISHED);
        given(lotRepository.findById(lot.getId())).willReturn(Optional.of(lot));
        given(lotRepository.findAll()).willReturn(List.of(lot));
        doThrow(new InvalidLotStateException("перехід заборонено"))
                .when(statusChanger).transition(lot, LotStatus.PUBLISHED, "Публікація лоту");

        assertThrows(InvalidLotStateException.class, () -> service.publish(lot.getId()));

        assertNull(lot.getPublishedAt());
        verify(lotRepository).findById(lot.getId());
        verify(lotRepository).findAll();
        verify(lotRepository, never()).save(any());
        verify(statusChanger).transition(lot, LotStatus.PUBLISHED, "Публікація лоту");
        verifyNoMoreInteractions(lotRepository, statusChanger);
    }

    @Test
    void publish_unknownLot_throwsLotNotFound() {
        UUID id = UUID.randomUUID();
        given(lotRepository.findById(id)).willReturn(Optional.empty());

        assertThrows(LotNotFoundException.class, () -> service.publish(id));

        verify(lotRepository).findById(id);
        verifyNoMoreInteractions(lotRepository);
        verifyNoInteractions(statusChanger);
    }

    @Test
    void cancel_allowedState_transitionsAndSaves() {
        FoodLot lot = lot(LotStatus.PUBLISHED);
        given(lotRepository.findById(lot.getId())).willReturn(Optional.of(lot));
        given(lotRepository.save(lot)).willReturn(lot);

        FoodLot cancelled = service.cancel(lot.getId());

        assertSame(lot, cancelled);
        verify(statusChanger).transition(lot, LotStatus.CANCELLED, "Скасовано донором");
        verify(lotRepository).findById(lot.getId());
        verify(lotRepository).save(lot);
        verifyNoMoreInteractions(lotRepository, statusChanger);
    }

    @Test
    void cancel_reservedLot_invalidTransition_doesNotSave() {
        FoodLot lot = lot(LotStatus.RESERVED);
        given(lotRepository.findById(lot.getId())).willReturn(Optional.of(lot));
        doThrow(new InvalidLotStateException("перехід заборонено"))
                .when(statusChanger).transition(lot, LotStatus.CANCELLED, "Скасовано донором");

        assertThrows(InvalidLotStateException.class, () -> service.cancel(lot.getId()));

        verify(lotRepository).findById(lot.getId());
        verify(lotRepository, never()).save(any());
        verify(statusChanger).transition(lot, LotStatus.CANCELLED, "Скасовано донором");
        verifyNoMoreInteractions(lotRepository, statusChanger);
    }

    @Test
    void cancel_unknownLot_throwsLotNotFound() {
        UUID id = UUID.randomUUID();
        given(lotRepository.findById(id)).willReturn(Optional.empty());

        assertThrows(LotNotFoundException.class, () -> service.cancel(id));

        verify(lotRepository).findById(id);
        verifyNoMoreInteractions(lotRepository);
        verifyNoInteractions(statusChanger);
    }

    @Test
    void approve_approved_publishesWithPublishedAt() {
        FoodLot lot = lot(LotStatus.PENDING_MODERATION);
        given(lotRepository.findById(lot.getId())).willReturn(Optional.of(lot));
        given(lotRepository.save(lot)).willReturn(lot);

        FoodLot result = service.approve(lot.getId(), new ApprovalRequest(true, "Все добре"));

        assertEquals(NOW, result.getPublishedAt());
        verify(statusChanger).transition(lot, LotStatus.PUBLISHED, "Все добре");
        verify(lotRepository).findById(lot.getId());
        verify(lotRepository).save(lot);
        verifyNoMoreInteractions(lotRepository, statusChanger);
    }

    @Test
    void approve_rejected_returnsToDraftWithoutPublishedAt() {
        FoodLot lot = lot(LotStatus.PENDING_MODERATION);
        given(lotRepository.findById(lot.getId())).willReturn(Optional.of(lot));
        given(lotRepository.save(lot)).willReturn(lot);

        FoodLot result = service.approve(lot.getId(), new ApprovalRequest(false, "Немає фото"));

        assertNull(result.getPublishedAt());
        verify(statusChanger).transition(lot, LotStatus.DRAFT, "Немає фото");
        verify(lotRepository).findById(lot.getId());
        verify(lotRepository).save(lot);
        verifyNoMoreInteractions(lotRepository, statusChanger);
    }

    @Test
    void approve_withoutComment_usesDefaultComment() {
        FoodLot lot = lot(LotStatus.PENDING_MODERATION);
        given(lotRepository.findById(lot.getId())).willReturn(Optional.of(lot));
        given(lotRepository.save(lot)).willReturn(lot);

        service.approve(lot.getId(), new ApprovalRequest(true, null));

        verify(statusChanger).transition(lot, LotStatus.PUBLISHED, "Рішення модератора");
        verify(lotRepository).findById(lot.getId());
        verify(lotRepository).save(lot);
        verifyNoMoreInteractions(lotRepository, statusChanger);
    }

    @Test
    void approve_invalidTransition_doesNotSave() {
        FoodLot lot = lot(LotStatus.CANCELLED);
        given(lotRepository.findById(lot.getId())).willReturn(Optional.of(lot));
        doThrow(new InvalidLotStateException("перехід заборонено"))
                .when(statusChanger).transition(lot, LotStatus.PUBLISHED, "Ок");

        assertThrows(InvalidLotStateException.class,
                () -> service.approve(lot.getId(), new ApprovalRequest(true, "Ок")));

        assertNull(lot.getPublishedAt());
        verify(lotRepository).findById(lot.getId());
        verify(lotRepository, never()).save(any());
        verify(statusChanger).transition(lot, LotStatus.PUBLISHED, "Ок");
        verifyNoMoreInteractions(lotRepository, statusChanger);
    }

    @Test
    void approve_unknownLot_throwsLotNotFound() {
        UUID id = UUID.randomUUID();
        given(lotRepository.findById(id)).willReturn(Optional.empty());

        assertThrows(LotNotFoundException.class, () -> service.approve(id, new ApprovalRequest(true, null)));

        verify(lotRepository).findById(id);
        verifyNoMoreInteractions(lotRepository);
        verifyNoInteractions(statusChanger);
    }

    @Test
    void getById_existingLot_returnsIt() {
        FoodLot lot = lot(LotStatus.DRAFT);
        given(lotRepository.findById(lot.getId())).willReturn(Optional.of(lot));

        assertSame(lot, service.getById(lot.getId()));

        verify(lotRepository).findById(lot.getId());
        verifyNoMoreInteractions(lotRepository);
        verifyNoInteractions(statusChanger);
    }

    @Test
    void getById_unknownLot_throwsLotNotFound() {
        UUID id = UUID.randomUUID();
        given(lotRepository.findById(id)).willReturn(Optional.empty());

        assertThrows(LotNotFoundException.class, () -> service.getById(id));

        verify(lotRepository).findById(id);
        verifyNoMoreInteractions(lotRepository);
        verifyNoInteractions(statusChanger);
    }

    @Test
    void findAll_filtersByStatusCategoryAndDonor() {
        FoodLot draftBakery = otherLot(DONOR, FoodCategory.BAKERY, LotStatus.DRAFT);
        FoodLot publishedBakery = otherLot(DONOR, FoodCategory.BAKERY, LotStatus.PUBLISHED);
        FoodLot publishedGrocery = otherLot(DONOR, FoodCategory.GROCERY, LotStatus.PUBLISHED);
        FoodLot otherDonorPublishedBakery = otherLot(OTHER_DONOR, FoodCategory.BAKERY, LotStatus.PUBLISHED);
        given(lotRepository.findAll())
                .willReturn(List.of(draftBakery, publishedBakery, publishedGrocery, otherDonorPublishedBakery));

        assertEquals(4, service.findAll(null, null, null).size());
        assertEquals(List.of(draftBakery), service.findAll(LotStatus.DRAFT, null, null));
        assertEquals(List.of(publishedGrocery), service.findAll(null, FoodCategory.GROCERY, null));
        assertEquals(List.of(otherDonorPublishedBakery), service.findAll(null, null, OTHER_DONOR));
        assertEquals(List.of(publishedBakery),
                service.findAll(LotStatus.PUBLISHED, FoodCategory.BAKERY, DONOR));

        verify(lotRepository, times(5)).findAll();
        verifyNoMoreInteractions(lotRepository);
        verifyNoInteractions(statusChanger);
    }
}
