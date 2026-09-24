package com.example.foodrescue.lot;

import com.example.foodrescue.common.FoodItem;
import com.example.foodrescue.common.FoodItemRepository;
import com.example.foodrescue.common.FoodLot;
import com.example.foodrescue.common.ItemUnit;
import com.example.foodrescue.common.LotRepository;
import com.example.foodrescue.common.LotStatus;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.example.foodrescue.lot.LotTestData.BASE_TIME;
import static com.example.foodrescue.lot.LotTestData.lot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class LotRepositoryTest {

    private static final UUID DONOR = UUID.fromString("00000000-0000-0000-0000-00000000d001");
    private static final UUID OTHER_DONOR = UUID.fromString("00000000-0000-0000-0000-00000000d002");
    private static final UUID VOLUNTEER = UUID.fromString("00000000-0000-0000-0000-0000000000b1");

    @Autowired
    private LotRepository lotRepository;

    @Autowired
    private FoodItemRepository foodItemRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    @Test
    void save_newLotWithAssignedIdAndVersion_insertsWithoutSelectBeforeInsert() {
        FoodLot lot = lot(DONOR, LotStatus.DRAFT, BASE_TIME, 2);
        Statistics stats = statistics();
        stats.clear();

        lotRepository.save(lot);
        entityManager.flush();

        assertEquals(3, stats.getEntityInsertCount());
        assertEquals(3, stats.getPrepareStatementCount());
    }

    @Test
    void save_cascadesInsertToItems() {
        FoodLot lot = lot(DONOR, LotStatus.DRAFT, BASE_TIME, 2);

        lotRepository.save(lot);
        flushAndClear();

        assertEquals(2, foodItemRepository.count());
        FoodLot loaded = lotRepository.findByIdWithItems(lot.getId()).orElseThrow();
        assertEquals(2, loaded.getItems().size());
        assertTrue(loaded.getItems().stream().allMatch(item -> item.getLot().equals(loaded)));
    }

    @Test
    void removeItem_deletesOrphanRowFromDatabase() {
        FoodLot lot = lotRepository.save(lot(DONOR, LotStatus.DRAFT, BASE_TIME, 2));
        flushAndClear();
        FoodLot loaded = lotRepository.findByIdWithItems(lot.getId()).orElseThrow();
        FoodItem removed = loaded.getItems().getFirst();

        loaded.removeItem(removed);
        flushAndClear();

        assertEquals(1, foodItemRepository.count());
        assertTrue(foodItemRepository.findById(removed.getId()).isEmpty());
        assertEquals(1, lotRepository.findByIdWithItems(lot.getId()).orElseThrow().getItems().size());
    }

    @Test
    void replaceItems_deletesOldItemsAndInsertsNewOnes() {
        FoodLot lot = lotRepository.save(lot(DONOR, LotStatus.DRAFT, BASE_TIME, 2));
        flushAndClear();
        FoodLot loaded = lotRepository.findByIdWithItems(lot.getId()).orElseThrow();
        Set<UUID> oldIds = Set.copyOf(loaded.getItems().stream().map(FoodItem::getId).toList());

        loaded.replaceItems(List.of(new FoodItem("Нова позиція", BigDecimal.TEN, ItemUnit.LITER, null)));
        flushAndClear();

        List<FoodItem> items = foodItemRepository.findByLotId(lot.getId());
        assertEquals(1, items.size());
        assertEquals("Нова позиція", items.getFirst().getName());
        assertFalse(oldIds.contains(items.getFirst().getId()));
        assertEquals(1, foodItemRepository.count());
    }

    @Test
    void delete_lotRemovesAllItemsByCascade() {
        FoodLot lot = lotRepository.save(lot(DONOR, LotStatus.DRAFT, BASE_TIME, 3));
        flushAndClear();
        FoodLot loaded = lotRepository.findById(lot.getId()).orElseThrow();

        lotRepository.delete(loaded);
        flushAndClear();

        assertTrue(lotRepository.findById(lot.getId()).isEmpty());
        assertEquals(0, foodItemRepository.count());
    }

    @Test
    void findAllWithItems_returnsLotsNewestFirstWithItemsLoaded() {
        FoodLot oldest = lotRepository.save(lot(DONOR, LotStatus.DRAFT, BASE_TIME, 2));
        FoodLot newest = lotRepository.save(lot(DONOR, LotStatus.PUBLISHED, BASE_TIME.plusSeconds(20), 1));
        FoodLot middle = lotRepository.save(lot(OTHER_DONOR, LotStatus.PUBLISHED, BASE_TIME.plusSeconds(10), 3));
        flushAndClear();

        List<FoodLot> lots = lotRepository.findAllWithItems();

        assertEquals(List.of(newest.getId(), middle.getId(), oldest.getId()), lots.stream().map(FoodLot::getId).toList());
        assertEquals(List.of(1, 3, 2), lots.stream().map(lot -> lot.getItems().size()).toList());
    }

    @Test
    void findAllWithItems_loadsLotsWithItemsInSingleQuery() {
        for (int i = 0; i < 5; i++) {
            lotRepository.save(lot(DONOR, LotStatus.PUBLISHED, BASE_TIME.plusSeconds(i), 2));
        }
        flushAndClear();
        Statistics stats = statistics();
        stats.clear();

        List<FoodLot> lots = lotRepository.findAllWithItems();
        lots.forEach(lot -> lot.getItems().size());

        assertEquals(5, lots.size());
        assertEquals(1, stats.getPrepareStatementCount());
    }

    @Test
    void findAllByStatusWithItems_returnsOnlyLotsWithThatStatusInSingleQuery() {
        for (int i = 0; i < 3; i++) {
            lotRepository.save(lot(DONOR, LotStatus.PUBLISHED, BASE_TIME.plusSeconds(i), 2));
        }
        lotRepository.save(lot(DONOR, LotStatus.DRAFT, BASE_TIME, 2));
        lotRepository.save(lot(DONOR, LotStatus.CANCELLED, BASE_TIME, 1));
        flushAndClear();
        Statistics stats = statistics();
        stats.clear();

        List<FoodLot> lots = lotRepository.findAllByStatusWithItems(LotStatus.PUBLISHED);
        lots.forEach(lot -> lot.getItems().size());

        assertEquals(3, lots.size());
        assertTrue(lots.stream().allMatch(lot -> lot.getStatus() == LotStatus.PUBLISHED));
        assertEquals(1, stats.getPrepareStatementCount());
    }

    @Test
    void findByIdWithItems_existingLot_returnsItWithItemsInSingleQuery() {
        FoodLot lot = lotRepository.save(lot(DONOR, LotStatus.DRAFT, BASE_TIME, 3));
        flushAndClear();
        Statistics stats = statistics();
        stats.clear();

        Optional<FoodLot> found = lotRepository.findByIdWithItems(lot.getId());
        found.orElseThrow().getItems().size();

        assertTrue(found.isPresent());
        assertEquals(3, found.get().getItems().size());
        assertEquals(1, stats.getPrepareStatementCount());
    }

    @Test
    void findByIdWithItems_unknownLot_returnsEmpty() {
        assertTrue(lotRepository.findByIdWithItems(UUID.randomUUID()).isEmpty());
    }

    @Test
    void countByDonorOrgId_countsOnlyLotsOfThatDonor() {
        lotRepository.save(lot(DONOR, LotStatus.DRAFT, BASE_TIME, 1));
        lotRepository.save(lot(DONOR, LotStatus.CANCELLED, BASE_TIME, 1));
        lotRepository.save(lot(OTHER_DONOR, LotStatus.DRAFT, BASE_TIME, 1));
        flushAndClear();

        assertEquals(2, lotRepository.countByDonorOrgId(DONOR));
        assertEquals(1, lotRepository.countByDonorOrgId(OTHER_DONOR));
        assertEquals(0, lotRepository.countByDonorOrgId(UUID.randomUUID()));
    }

    @Test
    void countByDonorOrgIdAndStatus_countsOnlyMatchingStatus() {
        lotRepository.save(lot(DONOR, LotStatus.CANCELLED, BASE_TIME, 1));
        lotRepository.save(lot(DONOR, LotStatus.CANCELLED, BASE_TIME, 1));
        lotRepository.save(lot(DONOR, LotStatus.DRAFT, BASE_TIME, 1));
        lotRepository.save(lot(OTHER_DONOR, LotStatus.CANCELLED, BASE_TIME, 1));
        flushAndClear();

        assertEquals(2, lotRepository.countByDonorOrgIdAndStatus(DONOR, LotStatus.CANCELLED));
        assertEquals(1, lotRepository.countByDonorOrgIdAndStatus(DONOR, LotStatus.DRAFT));
        assertEquals(0, lotRepository.countByDonorOrgIdAndStatus(DONOR, LotStatus.PUBLISHED));
    }

    @Test
    void existsByReservedByVolunteerIdAndStatusIn_findsLotsReservedByVolunteerInGivenStatuses() {
        FoodLot reserved = lot(DONOR, LotStatus.RESERVED, BASE_TIME, 1);
        reserved.setReservedByVolunteerId(VOLUNTEER);
        FoodLot delivered = lot(DONOR, LotStatus.DELIVERED, BASE_TIME, 1);
        delivered.setReservedByVolunteerId(UUID.randomUUID());
        lotRepository.save(reserved);
        lotRepository.save(delivered);
        flushAndClear();

        assertTrue(lotRepository.existsByReservedByVolunteerIdAndStatusIn(
                VOLUNTEER, List.of(LotStatus.RESERVED, LotStatus.PICKED_UP)));
        assertFalse(lotRepository.existsByReservedByVolunteerIdAndStatusIn(
                VOLUNTEER, List.of(LotStatus.PICKED_UP)));
        assertFalse(lotRepository.existsByReservedByVolunteerIdAndStatusIn(
                UUID.randomUUID(), List.of(LotStatus.RESERVED, LotStatus.PICKED_UP)));
    }
}
