package com.example.foodrescue.common;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Component
public class DemoData implements CommandLineRunner {

    public static final UUID DONOR = UUID.fromString("00000000-0000-0000-0000-00000000d001");
    public static final UUID LOT_DRAFT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    public static final UUID LOT_PUBLISHED = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    public static final UUID LOT_PUBLISHED_BIG = UUID.fromString("00000000-0000-0000-0000-0000000000a3");
    public static final UUID LOT_RESERVED = UUID.fromString("00000000-0000-0000-0000-0000000000a4");
    public static final UUID DEMO_VOLUNTEER = UUID.fromString("00000000-0000-0000-0000-00000000b1");

    private final LotRepository lotRepository;

    public DemoData(LotRepository lotRepository) {
        this.lotRepository = lotRepository;
    }

    @Override
    public void run(String... args) {
        Instant now = Instant.now();

        lotRepository.save(draftLot(now));
        lotRepository.save(publishedLot(now));
        lotRepository.save(publishedBigLot(now));
        lotRepository.save(reservedLot(now));
    }

    private FoodLot draftLot(Instant now) {
        FoodLot lot = new FoodLot();
        lot.setId(LOT_DRAFT);
        lot.setDonorOrgId(DONOR);
        lot.setTitle("Хліб і випічка (чернетка)");
        lot.setCategory(FoodCategory.BAKERY);
        lot.setItems(List.of(new FoodItem("Хліб пшеничний", BigDecimal.valueOf(5), ItemUnit.KG, bestBefore(now, 2))));
        lot.setTotalWeightKg(BigDecimal.valueOf(5));
        lot.setStorageCondition(StorageCondition.ROOM);
        lot.setPickupAddress("вул. Хлібна, 1, Київ");
        lot.setPickupFrom(now.plus(2, ChronoUnit.HOURS));
        lot.setPickupTo(now.plus(4, ChronoUnit.HOURS));
        lot.setCreatedAt(now);
        lot.setStatus(LotStatus.DRAFT);
        return lot;
    }

    private FoodLot publishedLot(Instant now) {
        FoodLot lot = new FoodLot();
        lot.setId(LOT_PUBLISHED);
        lot.setDonorOrgId(DONOR);
        lot.setTitle("Хліб і випічка");
        lot.setCategory(FoodCategory.BAKERY);
        lot.setItems(List.of(new FoodItem("Хліб житній", BigDecimal.valueOf(5), ItemUnit.KG, bestBefore(now, 2))));
        lot.setTotalWeightKg(BigDecimal.valueOf(5));
        lot.setStorageCondition(StorageCondition.ROOM);
        lot.setPickupAddress("вул. Хлібна, 2, Київ");
        lot.setPickupFrom(now.plus(2, ChronoUnit.HOURS));
        lot.setPickupTo(now.plus(4, ChronoUnit.HOURS));
        lot.setCreatedAt(now.minus(1, ChronoUnit.HOURS));
        lot.setPublishedAt(now.minus(1, ChronoUnit.HOURS));
        lot.setStatus(LotStatus.PUBLISHED);
        return lot;
    }

    private FoodLot publishedBigLot(Instant now) {
        FoodLot lot = new FoodLot();
        lot.setId(LOT_PUBLISHED_BIG);
        lot.setDonorOrgId(DONOR);
        lot.setTitle("Готові страви з кухні");
        lot.setCategory(FoodCategory.PREPARED_MEAL);
        lot.setItems(List.of(new FoodItem("Суп овочевий", BigDecimal.valueOf(25), ItemUnit.KG, bestBefore(now, 1))));
        lot.setTotalWeightKg(BigDecimal.valueOf(25));
        lot.setStorageCondition(StorageCondition.CHILLED);
        lot.setPickupAddress("вул. Кухонна, 3, Київ");
        lot.setPickupFrom(now.plus(2, ChronoUnit.HOURS));
        lot.setPickupTo(now.plus(4, ChronoUnit.HOURS));
        lot.setCreatedAt(now);
        lot.setPublishedAt(now);
        lot.setStatus(LotStatus.PUBLISHED);
        return lot;
    }

    private FoodLot reservedLot(Instant now) {
        FoodLot lot = new FoodLot();
        lot.setId(LOT_RESERVED);
        lot.setDonorOrgId(DONOR);
        lot.setTitle("Овочі та фрукти");
        lot.setCategory(FoodCategory.VEGETABLES);
        lot.setItems(List.of(new FoodItem("Яблука", BigDecimal.valueOf(8), ItemUnit.KG, bestBefore(now, 3))));
        lot.setTotalWeightKg(BigDecimal.valueOf(8));
        lot.setStorageCondition(StorageCondition.ROOM);
        lot.setPickupAddress("вул. Ринкова, 4, Київ");
        lot.setPickupFrom(now.plus(1, ChronoUnit.HOURS));
        lot.setPickupTo(now.plus(3, ChronoUnit.HOURS));
        lot.setCreatedAt(now.minus(2, ChronoUnit.HOURS));
        lot.setPublishedAt(now.minus(2, ChronoUnit.HOURS));
        lot.setStatus(LotStatus.RESERVED);
        lot.setReservedByVolunteerId(DEMO_VOLUNTEER);
        lot.setReservedUntil(now.plus(30, ChronoUnit.MINUTES));
        return lot;
    }

    private LocalDate bestBefore(Instant now, int daysAhead) {
        return LocalDate.ofInstant(now.plus(daysAhead, ChronoUnit.DAYS), ZoneOffset.UTC);
    }
}
