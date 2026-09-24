package com.example.foodrescue.lot;

import com.example.foodrescue.common.FoodItem;
import com.example.foodrescue.common.FoodItemRepository;
import com.example.foodrescue.common.FoodItemResponse;
import com.example.foodrescue.common.FoodLot;
import com.example.foodrescue.common.ItemUnit;
import com.example.foodrescue.common.LotNotFoundException;
import com.example.foodrescue.common.LotRepository;
import com.example.foodrescue.common.LotStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class LotItemServiceImplTest {

    private static final LocalDate BEST_BEFORE = LocalDate.parse("2026-10-01");

    @Mock
    private LotRepository lotRepository;

    @Mock
    private FoodItemRepository foodItemRepository;

    private LotItemServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LotItemServiceImpl(lotRepository, foodItemRepository);
    }

    private FoodLot lot(LotStatus status) {
        FoodLot lot = new FoodLot(UUID.randomUUID());
        lot.setStatus(status);
        return lot;
    }

    private FoodItem item(String name) {
        return new FoodItem(name, BigDecimal.valueOf(3), ItemUnit.KG, BEST_BEFORE);
    }

    private FoodItemRequest request() {
        return new FoodItemRequest("Морква", BigDecimal.valueOf(7), ItemUnit.PIECE, BEST_BEFORE.plusDays(2));
    }

    @Test
    void addItem_draftLot_addsItemToLotByCascade() {
        FoodLot lot = lot(LotStatus.DRAFT);
        given(lotRepository.findByIdWithItems(lot.getId())).willReturn(Optional.of(lot));

        FoodItemResponse response = service.addItem(lot.getId(), request());

        assertNotNull(response.id());
        assertEquals("Морква", response.name());
        assertEquals(1, lot.getItems().size());
        assertSame(lot, lot.getItems().getFirst().getLot());
        verify(lotRepository).findByIdWithItems(lot.getId());
        verifyNoMoreInteractions(lotRepository);
        verifyNoInteractions(foodItemRepository);
    }

    @Test
    void addItem_lotNotDraft_throwsAndDoesNotAdd() {
        FoodLot lot = lot(LotStatus.PUBLISHED);
        given(lotRepository.findByIdWithItems(lot.getId())).willReturn(Optional.of(lot));

        assertThrows(LotNotDraftException.class, () -> service.addItem(lot.getId(), request()));

        assertTrue(lot.getItems().isEmpty());
        verify(lotRepository).findByIdWithItems(lot.getId());
        verifyNoMoreInteractions(lotRepository);
        verifyNoInteractions(foodItemRepository);
    }

    @Test
    void addItem_unknownLot_throwsLotNotFound() {
        UUID lotId = UUID.randomUUID();
        given(lotRepository.findByIdWithItems(lotId)).willReturn(Optional.empty());

        assertThrows(LotNotFoundException.class, () -> service.addItem(lotId, request()));

        verify(lotRepository).findByIdWithItems(lotId);
        verifyNoMoreInteractions(lotRepository);
        verifyNoInteractions(foodItemRepository);
    }

    @Test
    void findItems_existingLot_returnsItsItems() {
        UUID lotId = UUID.randomUUID();
        FoodItem first = item("Хліб");
        FoodItem second = item("Молоко");
        given(lotRepository.existsById(lotId)).willReturn(true);
        given(foodItemRepository.findByLotId(lotId)).willReturn(List.of(first, second));

        List<FoodItemResponse> result = service.findItems(lotId);

        assertEquals(List.of(first.getId(), second.getId()), result.stream().map(FoodItemResponse::id).toList());
        verify(lotRepository).existsById(lotId);
        verify(foodItemRepository).findByLotId(lotId);
        verifyNoMoreInteractions(lotRepository, foodItemRepository);
    }

    @Test
    void findItems_unknownLot_throwsLotNotFound() {
        UUID lotId = UUID.randomUUID();
        given(lotRepository.existsById(lotId)).willReturn(false);

        assertThrows(LotNotFoundException.class, () -> service.findItems(lotId));

        verify(lotRepository).existsById(lotId);
        verifyNoMoreInteractions(lotRepository);
        verifyNoInteractions(foodItemRepository);
    }

    @Test
    void getItem_existingItem_returnsIt() {
        UUID lotId = UUID.randomUUID();
        FoodItem item = item("Хліб");
        given(lotRepository.existsById(lotId)).willReturn(true);
        given(foodItemRepository.findByIdAndLotId(item.getId(), lotId)).willReturn(Optional.of(item));

        FoodItemResponse response = service.getItem(lotId, item.getId());

        assertEquals(item.getId(), response.id());
        assertEquals("Хліб", response.name());
        verify(lotRepository).existsById(lotId);
        verify(foodItemRepository).findByIdAndLotId(item.getId(), lotId);
        verifyNoMoreInteractions(lotRepository, foodItemRepository);
    }

    @Test
    void getItem_itemOfAnotherLot_throwsFoodItemNotFound() {
        UUID lotId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        given(lotRepository.existsById(lotId)).willReturn(true);
        given(foodItemRepository.findByIdAndLotId(itemId, lotId)).willReturn(Optional.empty());

        assertThrows(FoodItemNotFoundException.class, () -> service.getItem(lotId, itemId));

        verify(lotRepository).existsById(lotId);
        verify(foodItemRepository).findByIdAndLotId(itemId, lotId);
        verifyNoMoreInteractions(lotRepository, foodItemRepository);
    }

    @Test
    void getItem_unknownLot_throwsLotNotFound() {
        UUID lotId = UUID.randomUUID();
        given(lotRepository.existsById(lotId)).willReturn(false);

        assertThrows(LotNotFoundException.class, () -> service.getItem(lotId, UUID.randomUUID()));

        verify(lotRepository).existsById(lotId);
        verifyNoMoreInteractions(lotRepository);
        verifyNoInteractions(foodItemRepository);
    }

    @Test
    void updateItem_draftLot_updatesFieldsAndSaves() {
        FoodLot lot = lot(LotStatus.DRAFT);
        FoodItem item = item("Хліб");
        given(lotRepository.findById(lot.getId())).willReturn(Optional.of(lot));
        given(foodItemRepository.findByIdAndLotId(item.getId(), lot.getId())).willReturn(Optional.of(item));
        given(foodItemRepository.save(item)).willReturn(item);

        FoodItemResponse response = service.updateItem(lot.getId(), item.getId(), request());

        assertEquals("Морква", response.name());
        assertEquals(BigDecimal.valueOf(7), response.quantity());
        assertEquals(ItemUnit.PIECE, response.unit());
        assertEquals(BEST_BEFORE.plusDays(2), response.bestBefore());
        verify(lotRepository).findById(lot.getId());
        verify(foodItemRepository).findByIdAndLotId(item.getId(), lot.getId());
        verify(foodItemRepository).save(item);
        verifyNoMoreInteractions(lotRepository, foodItemRepository);
    }

    @Test
    void updateItem_lotNotDraft_throwsAndDoesNotSave() {
        FoodLot lot = lot(LotStatus.RESERVED);
        given(lotRepository.findById(lot.getId())).willReturn(Optional.of(lot));

        assertThrows(LotNotDraftException.class, () -> service.updateItem(lot.getId(), UUID.randomUUID(), request()));

        verify(lotRepository).findById(lot.getId());
        verifyNoMoreInteractions(lotRepository);
        verifyNoInteractions(foodItemRepository);
    }

    @Test
    void updateItem_unknownLot_throwsLotNotFound() {
        UUID lotId = UUID.randomUUID();
        given(lotRepository.findById(lotId)).willReturn(Optional.empty());

        assertThrows(LotNotFoundException.class, () -> service.updateItem(lotId, UUID.randomUUID(), request()));

        verify(lotRepository).findById(lotId);
        verifyNoMoreInteractions(lotRepository);
        verifyNoInteractions(foodItemRepository);
    }

    @Test
    void updateItem_unknownItem_throwsFoodItemNotFoundAndDoesNotSave() {
        FoodLot lot = lot(LotStatus.DRAFT);
        UUID itemId = UUID.randomUUID();
        given(lotRepository.findById(lot.getId())).willReturn(Optional.of(lot));
        given(foodItemRepository.findByIdAndLotId(itemId, lot.getId())).willReturn(Optional.empty());

        assertThrows(FoodItemNotFoundException.class, () -> service.updateItem(lot.getId(), itemId, request()));

        verify(lotRepository).findById(lot.getId());
        verify(foodItemRepository).findByIdAndLotId(itemId, lot.getId());
        verify(foodItemRepository, never()).save(any());
        verifyNoMoreInteractions(lotRepository, foodItemRepository);
    }

    @Test
    void deleteItem_draftLot_removesItemFromLotByOrphanRemoval() {
        FoodLot lot = lot(LotStatus.DRAFT);
        FoodItem item = item("Хліб");
        lot.addItem(item);
        given(lotRepository.findByIdWithItems(lot.getId())).willReturn(Optional.of(lot));
        given(foodItemRepository.findByIdAndLotId(item.getId(), lot.getId())).willReturn(Optional.of(item));

        service.deleteItem(lot.getId(), item.getId());

        assertTrue(lot.getItems().isEmpty());
        assertNull(item.getLot());
        verify(lotRepository).findByIdWithItems(lot.getId());
        verify(foodItemRepository).findByIdAndLotId(item.getId(), lot.getId());
        verifyNoMoreInteractions(lotRepository, foodItemRepository);
    }

    @Test
    void deleteItem_lotNotDraft_throwsAndKeepsItem() {
        FoodLot lot = lot(LotStatus.PICKED_UP);
        FoodItem item = item("Хліб");
        lot.addItem(item);
        given(lotRepository.findByIdWithItems(lot.getId())).willReturn(Optional.of(lot));

        assertThrows(LotNotDraftException.class, () -> service.deleteItem(lot.getId(), item.getId()));

        assertEquals(1, lot.getItems().size());
        verify(lotRepository).findByIdWithItems(lot.getId());
        verifyNoMoreInteractions(lotRepository);
        verifyNoInteractions(foodItemRepository);
    }

    @Test
    void deleteItem_unknownLot_throwsLotNotFound() {
        UUID lotId = UUID.randomUUID();
        given(lotRepository.findByIdWithItems(lotId)).willReturn(Optional.empty());

        assertThrows(LotNotFoundException.class, () -> service.deleteItem(lotId, UUID.randomUUID()));

        verify(lotRepository).findByIdWithItems(lotId);
        verifyNoMoreInteractions(lotRepository);
        verifyNoInteractions(foodItemRepository);
    }

    @Test
    void deleteItem_unknownItem_throwsFoodItemNotFound() {
        FoodLot lot = lot(LotStatus.DRAFT);
        UUID itemId = UUID.randomUUID();
        given(lotRepository.findByIdWithItems(lot.getId())).willReturn(Optional.of(lot));
        given(foodItemRepository.findByIdAndLotId(itemId, lot.getId())).willReturn(Optional.empty());

        assertThrows(FoodItemNotFoundException.class, () -> service.deleteItem(lot.getId(), itemId));

        verify(lotRepository).findByIdWithItems(lot.getId());
        verify(foodItemRepository).findByIdAndLotId(itemId, lot.getId());
        verifyNoMoreInteractions(lotRepository, foodItemRepository);
    }
}
