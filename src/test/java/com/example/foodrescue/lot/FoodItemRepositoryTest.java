package com.example.foodrescue.lot;

import com.example.foodrescue.common.FoodItem;
import com.example.foodrescue.common.FoodItemRepository;
import com.example.foodrescue.common.FoodLot;
import com.example.foodrescue.common.LotRepository;
import com.example.foodrescue.common.LotStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.example.foodrescue.lot.LotTestData.BASE_TIME;
import static com.example.foodrescue.lot.LotTestData.lot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class FoodItemRepositoryTest {

    private static final UUID DONOR = UUID.fromString("00000000-0000-0000-0000-00000000d001");

    @Autowired
    private LotRepository lotRepository;

    @Autowired
    private FoodItemRepository foodItemRepository;

    @Autowired
    private TestEntityManager entityManager;

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void findByLotId_returnsOnlyItemsOfThatLot() {
        FoodLot first = lotRepository.save(lot(DONOR, LotStatus.DRAFT, BASE_TIME, 2));
        FoodLot second = lotRepository.save(lot(DONOR, LotStatus.DRAFT, BASE_TIME, 3));
        flushAndClear();

        List<FoodItem> firstItems = foodItemRepository.findByLotId(first.getId());
        List<FoodItem> secondItems = foodItemRepository.findByLotId(second.getId());

        assertEquals(2, firstItems.size());
        assertEquals(3, secondItems.size());
        assertTrue(foodItemRepository.findByLotId(UUID.randomUUID()).isEmpty());
    }

    @Test
    void findByIdAndLotId_itemOfThatLot_returnsIt() {
        FoodLot lot = lotRepository.save(lot(DONOR, LotStatus.DRAFT, BASE_TIME, 2));
        flushAndClear();
        FoodItem expected = foodItemRepository.findByLotId(lot.getId()).getFirst();

        Optional<FoodItem> found = foodItemRepository.findByIdAndLotId(expected.getId(), lot.getId());

        assertTrue(found.isPresent());
        assertEquals(expected.getId(), found.get().getId());
    }

    @Test
    void findByIdAndLotId_itemOfAnotherLot_returnsEmpty() {
        FoodLot owner = lotRepository.save(lot(DONOR, LotStatus.DRAFT, BASE_TIME, 1));
        FoodLot other = lotRepository.save(lot(DONOR, LotStatus.DRAFT, BASE_TIME, 1));
        flushAndClear();
        FoodItem item = foodItemRepository.findByLotId(owner.getId()).getFirst();

        assertTrue(foodItemRepository.findByIdAndLotId(item.getId(), other.getId()).isEmpty());
        assertTrue(foodItemRepository.findByIdAndLotId(UUID.randomUUID(), owner.getId()).isEmpty());
    }
}
