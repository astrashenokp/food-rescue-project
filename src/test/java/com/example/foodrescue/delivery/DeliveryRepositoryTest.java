package com.example.foodrescue.delivery;

import com.example.foodrescue.common.FoodCategory;
import com.example.foodrescue.common.FoodLot;
import com.example.foodrescue.common.LotRepository;
import com.example.foodrescue.common.LotStatus;
import com.example.foodrescue.common.StorageCondition;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class DeliveryRepositoryTest {

    @Autowired private DeliveryRepository deliveryRepository;
    @Autowired private DestinationPointRepository destinationPointRepository;
    @Autowired private LotRepository lotRepository;
    @Autowired private TestEntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;

    private FoodLot lot() {
        FoodLot lot = new FoodLot(UUID.randomUUID());
        lot.setDonorOrgId(UUID.randomUUID());
        lot.setTitle("Тестовий лот");
        lot.setCategory(FoodCategory.BAKERY);
        lot.setTotalWeightKg(new BigDecimal("10.00"));
        lot.setStorageCondition(StorageCondition.ROOM);
        lot.setPickupAddress("вул. Тестова, 1");
        lot.setPickupFrom(Instant.parse("2026-09-20T10:00:00Z"));
        lot.setPickupTo(Instant.parse("2026-09-20T12:00:00Z"));
        lot.setCreatedAt(Instant.parse("2026-09-20T08:00:00Z"));
        lot.setStatus(LotStatus.PICKED_UP);
        return lot;
    }

    private DestinationPoint point(String name) {
        return new DestinationPoint(
                UUID.randomUUID(),
                UUID.randomUUID(),
                name,
                "вул. Центральна, 10",
                "09:00-18:00",
                Set.of(FoodCategory.BAKERY));
    }

    private Delivery saveDelivery(FoodLot lot, DestinationPoint point) {
        lotRepository.save(lot);
        destinationPointRepository.save(point);
        Delivery delivery = new Delivery(
                UUID.randomUUID(), lot, UUID.randomUUID(), Instant.parse("2026-09-20T11:00:00Z"), new BigDecimal("10.00"), false);
        delivery.setDestinationPoint(point);
        delivery.setDeliveredAt(Instant.parse("2026-09-20T11:30:00Z"));
        delivery.setConfirmationCode("123456");
        return deliveryRepository.save(delivery);
    }

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    @Test
    void existsByDestinationPointId_findsUsedPoint() {
        DestinationPoint point = point("Центр 1");
        saveDelivery(lot(), point);
        entityManager.flush();
        entityManager.clear();

        assertTrue(deliveryRepository.existsByDestinationPointId(point.getId()));
    }

    @Test
    void findByLotIdWithLot_loadsOneToOneInOneQuery() {
        FoodLot lot = lot();
        saveDelivery(lot, point("Центр 2"));
        entityManager.flush();
        entityManager.clear();
        Statistics stats = statistics();
        stats.clear();

        Delivery loaded = deliveryRepository.findByLotIdWithLot(lot.getId()).orElseThrow();
        loaded.getLot().getStatus();

        assertEquals(1, stats.getPrepareStatementCount());
    }

    @Test
    void findAllWithLotAndPoint_avoidsNPlusOne() {
        for (int i = 1; i <= 4; i++) {
            saveDelivery(lot(), point("Центр " + (i + 2)));
        }
        entityManager.flush();
        entityManager.clear();
        Statistics stats = statistics();
        stats.clear();

        List<Delivery> deliveries = deliveryRepository.findAllWithLotAndPoint();
        deliveries.forEach(delivery -> {
            delivery.getLot().getStatus();
            delivery.getDestinationPoint().getName();
        });

        assertEquals(4, deliveries.size());
        assertEquals(1, stats.getPrepareStatementCount());
    }

    @Test
    void destinationPointCategories_areLoadedWithJoinFetchInOneQuery() {
        DestinationPoint point = point("Центр категорій");
        destinationPointRepository.save(point);
        entityManager.flush();
        entityManager.clear();
        Statistics stats = statistics();
        stats.clear();

        DestinationPoint loaded = destinationPointRepository.findByIdWithCategories(point.getId()).orElseThrow();
        loaded.getAcceptedCategories().size();

        assertEquals(1, stats.getPrepareStatementCount());
    }
}
