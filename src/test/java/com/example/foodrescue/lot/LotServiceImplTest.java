package com.example.foodrescue.lot;

import com.example.foodrescue.common.FoodCategory;
import com.example.foodrescue.common.FoodItem;
import com.example.foodrescue.common.FoodLot;
import com.example.foodrescue.common.InvalidLotStateException;
import com.example.foodrescue.common.ItemUnit;
import com.example.foodrescue.common.LotNotFoundException;
import com.example.foodrescue.common.LotRepository;
import com.example.foodrescue.common.LotResponse;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        FoodLot lot = new FoodLot(UUID.randomUUID());
        lot.setDonorOrgId(DONOR);
        lot.setCategory(FoodCategory.BAKERY);
        lot.setTitle("Старий заголовок");
        lot.setCreatedAt(NOW.minus(1, ChronoUnit.DAYS));
        lot.setStatus(status);
        lot.addItem(new FoodItem("Старий хліб", BigDecimal.ONE, ItemUnit.KG, null));
        return lot;
    }

    private FoodLot otherLot(UUID donor, FoodCategory category, LotStatus status) {
        FoodLot lot = new FoodLot(UUID.randomUUID());
        lot.setDonorOrgId(donor);
        lot.setCategory(category);
        lot.setStatus(status);
        return lot;
    }

    private void givenLotWithItems(FoodLot lot) {
        given(lotRepository.findByIdWithItems(lot.getId())).willReturn(Optional.of(lot));
        given(lotRepository.save(lot)).willReturn(lot);
    }

    @Test
    void create_validRequest_savesDraftLot() {
        given(lotRepository.save(any(FoodLot.class))).willAnswer(invocation -> invocation.getArgument(0));

        LotResponse created = service.create(validRequest());

        assertNotNull(created.id());
        assertEquals(LotStatus.DRAFT, created.status());
        assertEquals(NOW, created.createdAt());
        assertNull(created.publishedAt());
        assertEquals("Хліб і випічка", created.title());
        assertEquals(FoodCategory.BAKERY, created.category());
        assertEquals(1, created.items().size());
        assertEquals("Хліб", created.items().getFirst().name());
        verify(lotRepository).save(any(FoodLot.class));
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

        LotResponse created = service.create(request(category, from, from.plus(minMinutes, ChronoUnit.MINUTES)));

        assertEquals(category, created.category());
        verify(lotRepository).save(any(FoodLot.class));
        verifyNoMoreInteractions(lotRepository);
        verifyNoInteractions(statusChanger);
    }

    @Test
    void findAll_withoutStatus_readsAllLotsWithItemsInOneCall() {
        FoodLot draft = otherLot(DONOR, FoodCategory.BAKERY, LotStatus.DRAFT);
        FoodLot published = otherLot(DONOR, FoodCategory.GROCERY, LotStatus.PUBLISHED);
        given(lotRepository.findAllWithItems()).willReturn(List.of(draft, published));

        List<LotResponse> result = service.findAll(null, null, null);

        assertEquals(2, result.size());
        verify(lotRepository).findAllWithItems();
        verifyNoMoreInteractions(lotRepository);
        verifyNoInteractions(statusChanger);
    }

    @Test
    void findAll_withStatus_readsOnlyThatStatusWithItems() {
        FoodLot published = otherLot(DONOR, FoodCategory.BAKERY, LotStatus.PUBLISHED);
        given(lotRepository.findAllByStatusWithItems(LotStatus.PUBLISHED)).willReturn(List.of(published));

        List<LotResponse> result = service.findAll(LotStatus.PUBLISHED, null, null);

        assertEquals(1, result.size());
        assertEquals(LotStatus.PUBLISHED, result.getFirst().status());
        verify(lotRepository).findAllByStatusWithItems(LotStatus.PUBLISHED);
        verifyNoMoreInteractions(lotRepository);
        verifyNoInteractions(statusChanger);
    }

    @Test
    void findAll_categoryAndDonorFilters_areAppliedToFetchedLots() {
        FoodLot bakery = otherLot(DONOR, FoodCategory.BAKERY, LotStatus.PUBLISHED);
        FoodLot grocery = otherLot(DONOR, FoodCategory.GROCERY, LotStatus.PUBLISHED);
        FoodLot otherDonorBakery = otherLot(OTHER_DONOR, FoodCategory.BAKERY, LotStatus.PUBLISHED);
        given(lotRepository.findAllWithItems()).willReturn(List.of(bakery, grocery, otherDonorBakery));

        assertEquals(List.of(bakery.getId()),
                service.findAll(null, FoodCategory.BAKERY, DONOR).stream().map(LotResponse::id).toList());
        assertEquals(List.of(grocery.getId()),
                service.findAll(null, FoodCategory.GROCERY, null).stream().map(LotResponse::id).toList());
        assertEquals(List.of(otherDonorBakery.getId()),
                service.findAll(null, null, OTHER_DONOR).stream().map(LotResponse::id).toList());

        verify(lotRepository, times(3)).findAllWithItems();
        verifyNoMoreInteractions(lotRepository);
        verifyNoInteractions(statusChanger);
    }

    @Test
    void getById_existingLot_returnsResponseWithItems() {
        FoodLot lot = lot(LotStatus.DRAFT);
        given(lotRepository.findByIdWithItems(lot.getId())).willReturn(Optional.of(lot));

        LotResponse result = service.getById(lot.getId());

        assertEquals(lot.getId(), result.id());
        assertEquals(1, result.items().size());
        verify(lotRepository).findByIdWithItems(lot.getId());
        verifyNoMoreInteractions(lotRepository);
        verifyNoInteractions(statusChanger);
    }

    @Test
    void getById_unknownLot_throwsLotNotFound() {
        UUID id = UUID.randomUUID();
        given(lotRepository.findByIdWithItems(id)).willReturn(Optional.empty());

        assertThrows(LotNotFoundException.class, () -> service.getById(id));

        verify(lotRepository).findByIdWithItems(id);
        verifyNoMoreInteractions(lotRepository);
        verifyNoInteractions(statusChanger);
    }

    @Test
    void update_draftLot_replacesFieldsAndItemsAndKeepsStatus() {
        FoodLot lot = lot(LotStatus.DRAFT);
        givenLotWithItems(lot);

        LotResponse updated = service.update(lot.getId(), validRequest());

        assertEquals("Хліб і випічка", updated.title());
        assertEquals(LotStatus.DRAFT, updated.status());
        assertEquals(NOW.minus(1, ChronoUnit.DAYS), updated.createdAt());
        assertEquals(1, updated.items().size());
        assertEquals("Хліб", updated.items().getFirst().name());
        verify(lotRepository).findByIdWithItems(lot.getId());
        verify(lotRepository).save(lot);
        verifyNoMoreInteractions(lotRepository);
        verifyNoInteractions(statusChanger);
    }

    @Test
    void update_lotNotDraft_throwsLotNotDraftAndDoesNotSave() {
        FoodLot lot = lot(LotStatus.PUBLISHED);
        given(lotRepository.findByIdWithItems(lot.getId())).willReturn(Optional.of(lot));

        assertThrows(LotNotDraftException.class, () -> service.update(lot.getId(), validRequest()));

        verify(lotRepository).findByIdWithItems(lot.getId());
        verify(lotRepository, never()).save(any());
        verifyNoMoreInteractions(lotRepository);
        verifyNoInteractions(statusChanger);
    }

    @Test
    void update_unknownLot_throwsLotNotFound() {
        UUID id = UUID.randomUUID();
        given(lotRepository.findByIdWithItems(id)).willReturn(Optional.empty());

        assertThrows(LotNotFoundException.class, () -> service.update(id, validRequest()));

        verify(lotRepository).findByIdWithItems(id);
        verifyNoMoreInteractions(lotRepository);
        verifyNoInteractions(statusChanger);
    }

    @Test
    void update_invalidPickupWindow_throwsAndDoesNotSave() {
        FoodLot lot = lot(LotStatus.DRAFT);
        given(lotRepository.findByIdWithItems(lot.getId())).willReturn(Optional.of(lot));
        Instant from = NOW.plus(2, ChronoUnit.HOURS);

        assertThrows(InvalidPickupWindowException.class,
                () -> service.update(lot.getId(), request(FoodCategory.BAKERY, from, from.plus(10, ChronoUnit.MINUTES))));

        assertEquals("Старий заголовок", lot.getTitle());
        verify(lotRepository).findByIdWithItems(lot.getId());
        verify(lotRepository, never()).save(any());
        verifyNoMoreInteractions(lotRepository);
        verifyNoInteractions(statusChanger);
    }

    @Test
    void delete_draftLot_deletesIt() {
        FoodLot lot = lot(LotStatus.DRAFT);
        given(lotRepository.findById(lot.getId())).willReturn(Optional.of(lot));

        service.delete(lot.getId());

        verify(lotRepository).findById(lot.getId());
        verify(lotRepository).delete(lot);
        verifyNoMoreInteractions(lotRepository);
        verifyNoInteractions(statusChanger);
    }

    @ParameterizedTest
    @CsvSource({"PENDING_MODERATION", "PUBLISHED", "RESERVED", "CANCELLED", "CONFIRMED"})
    void delete_lotNotDraft_throwsLotNotDraftAndDoesNotDelete(LotStatus status) {
        FoodLot lot = lot(status);
        given(lotRepository.findById(lot.getId())).willReturn(Optional.of(lot));

        assertThrows(LotNotDraftException.class, () -> service.delete(lot.getId()));

        verify(lotRepository).findById(lot.getId());
        verify(lotRepository, never()).delete(any());
        verifyNoMoreInteractions(lotRepository);
        verifyNoInteractions(statusChanger);
    }

    @Test
    void delete_unknownLot_throwsLotNotFound() {
        UUID id = UUID.randomUUID();
        given(lotRepository.findById(id)).willReturn(Optional.empty());

        assertThrows(LotNotFoundException.class, () -> service.delete(id));

        verify(lotRepository).findById(id);
        verify(lotRepository, never()).delete(any());
        verifyNoMoreInteractions(lotRepository);
        verifyNoInteractions(statusChanger);
    }

    @Test
    void publish_regularDonor_publishesAndSetsPublishedAt() {
        FoodLot lot = lot(LotStatus.DRAFT);
        givenLotWithItems(lot);
        given(lotRepository.countByDonorOrgId(DONOR)).willReturn(1L);

        LotResponse published = service.publish(lot.getId());

        assertEquals(NOW, published.publishedAt());
        verify(statusChanger).transition(lot, LotStatus.PUBLISHED, "Публікація лоту");
        verify(lotRepository).findByIdWithItems(lot.getId());
        verify(lotRepository).countByDonorOrgId(DONOR);
        verify(lotRepository).save(lot);
        verifyNoMoreInteractions(lotRepository, statusChanger);
    }

    @Test
    void publish_donorWithMoreThan20PercentCancelled_goesToModeration() {
        FoodLot lot = lot(LotStatus.DRAFT);
        givenLotWithItems(lot);
        given(lotRepository.countByDonorOrgId(DONOR)).willReturn(5L);
        given(lotRepository.countByDonorOrgIdAndStatus(DONOR, LotStatus.CANCELLED)).willReturn(2L);

        LotResponse result = service.publish(lot.getId());

        assertNull(result.publishedAt());
        verify(statusChanger).transition(lot, LotStatus.PENDING_MODERATION, "Публікація лоту");
        verify(lotRepository).findByIdWithItems(lot.getId());
        verify(lotRepository).countByDonorOrgId(DONOR);
        verify(lotRepository).countByDonorOrgIdAndStatus(DONOR, LotStatus.CANCELLED);
        verify(lotRepository).save(lot);
        verifyNoMoreInteractions(lotRepository, statusChanger);
    }

    @Test
    void publish_donorWithExactly20PercentCancelled_isPublished() {
        FoodLot lot = lot(LotStatus.DRAFT);
        givenLotWithItems(lot);
        given(lotRepository.countByDonorOrgId(DONOR)).willReturn(5L);
        given(lotRepository.countByDonorOrgIdAndStatus(DONOR, LotStatus.CANCELLED)).willReturn(1L);

        LotResponse result = service.publish(lot.getId());

        assertEquals(NOW, result.publishedAt());
        verify(statusChanger).transition(lot, LotStatus.PUBLISHED, "Публікація лоту");
        verify(lotRepository).findByIdWithItems(lot.getId());
        verify(lotRepository).countByDonorOrgId(DONOR);
        verify(lotRepository).countByDonorOrgIdAndStatus(DONOR, LotStatus.CANCELLED);
        verify(lotRepository).save(lot);
        verifyNoMoreInteractions(lotRepository, statusChanger);
    }

    @Test
    void publish_donorWithFewerThanFiveLots_ignoresCancellations() {
        FoodLot lot = lot(LotStatus.DRAFT);
        givenLotWithItems(lot);
        given(lotRepository.countByDonorOrgId(DONOR)).willReturn(4L);

        LotResponse result = service.publish(lot.getId());

        assertEquals(NOW, result.publishedAt());
        verify(statusChanger).transition(lot, LotStatus.PUBLISHED, "Публікація лоту");
        verify(lotRepository).findByIdWithItems(lot.getId());
        verify(lotRepository).countByDonorOrgId(DONOR);
        verify(lotRepository, never()).countByDonorOrgIdAndStatus(any(), any());
        verify(lotRepository).save(lot);
        verifyNoMoreInteractions(lotRepository, statusChanger);
    }

    @Test
    void publish_invalidTransition_doesNotSave() {
        FoodLot lot = lot(LotStatus.PUBLISHED);
        given(lotRepository.findByIdWithItems(lot.getId())).willReturn(Optional.of(lot));
        given(lotRepository.countByDonorOrgId(DONOR)).willReturn(1L);
        doThrow(new InvalidLotStateException("перехід заборонено"))
                .when(statusChanger).transition(lot, LotStatus.PUBLISHED, "Публікація лоту");

        assertThrows(InvalidLotStateException.class, () -> service.publish(lot.getId()));

        assertNull(lot.getPublishedAt());
        verify(lotRepository).findByIdWithItems(lot.getId());
        verify(lotRepository).countByDonorOrgId(DONOR);
        verify(lotRepository, never()).save(any());
        verify(statusChanger).transition(lot, LotStatus.PUBLISHED, "Публікація лоту");
        verifyNoMoreInteractions(lotRepository, statusChanger);
    }

    @Test
    void publish_unknownLot_throwsLotNotFound() {
        UUID id = UUID.randomUUID();
        given(lotRepository.findByIdWithItems(id)).willReturn(Optional.empty());

        assertThrows(LotNotFoundException.class, () -> service.publish(id));

        verify(lotRepository).findByIdWithItems(id);
        verifyNoMoreInteractions(lotRepository);
        verifyNoInteractions(statusChanger);
    }

    @Test
    void cancel_allowedState_transitionsAndSaves() {
        FoodLot lot = lot(LotStatus.PUBLISHED);
        givenLotWithItems(lot);

        LotResponse cancelled = service.cancel(lot.getId());

        assertEquals(lot.getId(), cancelled.id());
        verify(statusChanger).transition(lot, LotStatus.CANCELLED, "Скасовано донором");
        verify(lotRepository).findByIdWithItems(lot.getId());
        verify(lotRepository).save(lot);
        verifyNoMoreInteractions(lotRepository, statusChanger);
    }

    @Test
    void cancel_reservedLot_invalidTransition_doesNotSave() {
        FoodLot lot = lot(LotStatus.RESERVED);
        given(lotRepository.findByIdWithItems(lot.getId())).willReturn(Optional.of(lot));
        doThrow(new InvalidLotStateException("перехід заборонено"))
                .when(statusChanger).transition(lot, LotStatus.CANCELLED, "Скасовано донором");

        assertThrows(InvalidLotStateException.class, () -> service.cancel(lot.getId()));

        verify(lotRepository).findByIdWithItems(lot.getId());
        verify(lotRepository, never()).save(any());
        verify(statusChanger).transition(lot, LotStatus.CANCELLED, "Скасовано донором");
        verifyNoMoreInteractions(lotRepository, statusChanger);
    }

    @Test
    void cancel_unknownLot_throwsLotNotFound() {
        UUID id = UUID.randomUUID();
        given(lotRepository.findByIdWithItems(id)).willReturn(Optional.empty());

        assertThrows(LotNotFoundException.class, () -> service.cancel(id));

        verify(lotRepository).findByIdWithItems(id);
        verifyNoMoreInteractions(lotRepository);
        verifyNoInteractions(statusChanger);
    }

    @Test
    void approve_approved_publishesWithPublishedAt() {
        FoodLot lot = lot(LotStatus.PENDING_MODERATION);
        givenLotWithItems(lot);

        LotResponse result = service.approve(lot.getId(), new ApprovalRequest(true, "Все добре"));

        assertEquals(NOW, result.publishedAt());
        verify(statusChanger).transition(lot, LotStatus.PUBLISHED, "Все добре");
        verify(lotRepository).findByIdWithItems(lot.getId());
        verify(lotRepository).save(lot);
        verifyNoMoreInteractions(lotRepository, statusChanger);
    }

    @Test
    void approve_rejected_returnsToDraftWithoutPublishedAt() {
        FoodLot lot = lot(LotStatus.PENDING_MODERATION);
        givenLotWithItems(lot);

        LotResponse result = service.approve(lot.getId(), new ApprovalRequest(false, "Немає фото"));

        assertNull(result.publishedAt());
        verify(statusChanger).transition(lot, LotStatus.DRAFT, "Немає фото");
        verify(lotRepository).findByIdWithItems(lot.getId());
        verify(lotRepository).save(lot);
        verifyNoMoreInteractions(lotRepository, statusChanger);
    }

    @Test
    void approve_withoutComment_usesDefaultComment() {
        FoodLot lot = lot(LotStatus.PENDING_MODERATION);
        givenLotWithItems(lot);

        service.approve(lot.getId(), new ApprovalRequest(true, null));

        verify(statusChanger).transition(lot, LotStatus.PUBLISHED, "Рішення модератора");
        verify(lotRepository).findByIdWithItems(lot.getId());
        verify(lotRepository).save(lot);
        verifyNoMoreInteractions(lotRepository, statusChanger);
    }

    @Test
    void approve_invalidTransition_doesNotSave() {
        FoodLot lot = lot(LotStatus.CANCELLED);
        given(lotRepository.findByIdWithItems(lot.getId())).willReturn(Optional.of(lot));
        doThrow(new InvalidLotStateException("перехід заборонено"))
                .when(statusChanger).transition(lot, LotStatus.PUBLISHED, "Ок");

        assertThrows(InvalidLotStateException.class,
                () -> service.approve(lot.getId(), new ApprovalRequest(true, "Ок")));

        assertNull(lot.getPublishedAt());
        verify(lotRepository).findByIdWithItems(lot.getId());
        verify(lotRepository, never()).save(any());
        verify(statusChanger).transition(lot, LotStatus.PUBLISHED, "Ок");
        verifyNoMoreInteractions(lotRepository, statusChanger);
    }

    @Test
    void approve_unknownLot_throwsLotNotFound() {
        UUID id = UUID.randomUUID();
        given(lotRepository.findByIdWithItems(id)).willReturn(Optional.empty());

        assertThrows(LotNotFoundException.class, () -> service.approve(id, new ApprovalRequest(true, null)));

        verify(lotRepository).findByIdWithItems(id);
        verifyNoMoreInteractions(lotRepository);
        verifyNoInteractions(statusChanger);
    }
}
