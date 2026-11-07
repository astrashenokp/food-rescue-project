package com.example.foodrescue;

import com.example.foodrescue.common.FoodCategory;
import com.example.foodrescue.common.ItemUnit;
import com.example.foodrescue.common.StorageCondition;
import com.example.foodrescue.lot.FoodItemRequest;
import com.example.foodrescue.lot.LotRequest;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AuditTestSupport {

    private AuditTestSupport() {
    }

    public static LotRequest lotRequest(String title) {
        Instant now = Instant.now();
        return lotRequest(title, now.plus(2, ChronoUnit.HOURS), now.plus(4, ChronoUnit.HOURS));
    }

    public static LotRequest lotRequest(String title, Instant pickupFrom, Instant pickupTo) {
        return new LotRequest(UUID.randomUUID(), title, FoodCategory.BAKERY,
                List.of(new FoodItemRequest("Хліб", BigDecimal.valueOf(5), ItemUnit.KG, null)),
                BigDecimal.valueOf(5), StorageCondition.ROOM, "Київ, вул. Хлібна, 1", pickupFrom, pickupTo);
    }

    public static List<Map<String, Object>> auditRows(JdbcClient jdbc, String action) {
        return jdbc.sql("select * from audit_log where action = ? order by id")
                .param(action)
                .query()
                .listOfRows();
    }
}
