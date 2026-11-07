package com.example.foodrescue.lot;

import com.example.foodrescue.AuditTestSupport;
import com.example.foodrescue.IntegrationTest;
import com.example.foodrescue.common.BusinessException;
import com.example.foodrescue.common.ConflictException;
import com.example.foodrescue.common.LotRepository;
import com.example.foodrescue.common.LotStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
class LotAuditIntegrationTest {

    private static final String DONOR = "donor1";
    private static final String CREATE_LOT = "CREATE_LOT";
    private static final String UPDATE_LOT = "UPDATE_LOT";
    private static final String PUBLISH_LOT = "PUBLISH_LOT";
    private static final String CANCEL_LOT = "CANCEL_LOT";
    private static final String APPROVE_LOT = "APPROVE_LOT";
    private static final String TITLE = "Свіжа випічка";

    @Autowired
    LotService lotService;

    @Autowired
    LotRepository lotRepository;

    @Autowired
    JdbcClient jdbc;

    @Test
    @WithMockUser(username = DONOR, roles = "DONOR")
    void create_ShouldWriteSuccessRecord() {
        lotService.create(AuditTestSupport.lotRequest(TITLE));

        Map<String, Object> row = singleRow(CREATE_LOT);
        assertSucceeded(row, DONOR);
        assertThat((String) row.get("arguments")).contains(TITLE);
    }

    @Test
    @WithMockUser(username = DONOR, roles = "DONOR")
    void update_ShouldWriteSuccessRecord() {
        UUID id = saveLot(LotStatus.DRAFT);

        lotService.update(id, AuditTestSupport.lotRequest("Нова назва"));

        Map<String, Object> row = singleRow(UPDATE_LOT);
        assertSucceeded(row, DONOR);
        assertThat((String) row.get("arguments")).contains(id.toString()).contains("Нова назва");
    }

    @Test
    @WithMockUser(username = DONOR, roles = "DONOR")
    void publish_ShouldWriteSuccessRecord() {
        UUID id = saveLot(LotStatus.DRAFT);

        lotService.publish(id);

        Map<String, Object> row = singleRow(PUBLISH_LOT);
        assertSucceeded(row, DONOR);
        assertThat((String) row.get("result")).contains("PUBLISHED");
    }

    @Test
    @WithMockUser(username = DONOR, roles = "DONOR")
    void cancel_ShouldWriteSuccessRecord() {
        UUID id = saveLot(LotStatus.DRAFT);

        lotService.cancel(id);

        Map<String, Object> row = singleRow(CANCEL_LOT);
        assertSucceeded(row, DONOR);
        assertThat((String) row.get("result")).contains("CANCELLED");
    }

    @Test
    @WithMockUser(username = "admin1", roles = "ADMIN")
    void approve_ShouldWriteSuccessRecord() {
        UUID id = saveLot(LotStatus.DRAFT);

        lotService.approve(id, new ApprovalRequest(true, "Підтверджено"));

        assertSucceeded(singleRow(APPROVE_LOT), "admin1");
    }

    @Test
    @WithMockUser(username = DONOR, roles = "DONOR")
    void update_PublishedLot_ShouldWriteFailedRecordAndRethrow() {
        UUID id = saveLot(LotStatus.PUBLISHED);
        String titleBefore = titleOf(id);

        assertThatThrownBy(() -> lotService.update(id, AuditTestSupport.lotRequest("Нова назва")))
                .isInstanceOf(ConflictException.class);

        assertFailed(singleRow(UPDATE_LOT), DONOR, "LotNotDraftException");
        assertThat(titleOf(id)).isEqualTo(titleBefore);
    }

    @Test
    @WithMockUser(username = DONOR, roles = "DONOR")
    void cancel_ReservedLot_ShouldWriteFailedRecordAndKeepStatus() {
        UUID id = saveLot(LotStatus.RESERVED);

        assertThatThrownBy(() -> lotService.cancel(id)).isInstanceOf(ConflictException.class);

        assertFailed(singleRow(CANCEL_LOT), DONOR, "InvalidLotStateException");
        assertThat(statusOf(id)).isEqualTo("RESERVED");
    }

    @Test
    @WithMockUser(username = DONOR, roles = "DONOR")
    void create_PastPickupWindow_ShouldWriteFailedRecordAndRethrow() {
        long before = countLots();
        Instant now = Instant.now();
        LotRequest request = AuditTestSupport.lotRequest(TITLE, now.minus(1, ChronoUnit.HOURS),
                now.plus(2, ChronoUnit.HOURS));

        assertThatThrownBy(() -> lotService.create(request)).isInstanceOf(BusinessException.class);

        assertFailed(singleRow(CREATE_LOT), DONOR, "InvalidPickupWindowException");
        assertThat(countLots()).isEqualTo(before);
    }

    @Test
    @WithMockUser(username = "volunteer1", roles = "VOLUNTEER")
    void create_ByVolunteer_ShouldWriteDeniedRecordAndRethrow() {
        long before = countLots();

        assertThatThrownBy(() -> lotService.create(AuditTestSupport.lotRequest(TITLE)))
                .isInstanceOf(AuthorizationDeniedException.class);

        assertFailed(singleRow(CREATE_LOT), "volunteer1", "AuthorizationDeniedException");
        assertThat(countLots()).isEqualTo(before);
    }

    private UUID saveLot(LotStatus status) {
        return lotRepository.save(LotTestData.lot(UUID.randomUUID(), status, Instant.now(), 1)).getId();
    }

    private Map<String, Object> singleRow(String action) {
        List<Map<String, Object>> rows = AuditTestSupport.auditRows(jdbc, action);
        assertThat(rows).hasSize(1);
        return rows.getFirst();
    }

    private void assertSucceeded(Map<String, Object> row, String username) {
        assertThat(row.get("username")).isEqualTo(username);
        assertThat(row.get("success")).isEqualTo(true);
        assertThat(row.get("error_message")).isNull();
        assertThat(row.get("created_at")).isNotNull();
    }

    private void assertFailed(Map<String, Object> row, String username, String exceptionName) {
        assertThat(row.get("username")).isEqualTo(username);
        assertThat(row.get("success")).isEqualTo(false);
        assertThat((String) row.get("error_message")).contains(exceptionName);
    }

    private long countLots() {
        return jdbc.sql("select count(*) from food_lots").query(Long.class).single();
    }

    private String titleOf(UUID id) {
        return jdbc.sql("select title from food_lots where id = ?").param(id).query(String.class).single();
    }

    private String statusOf(UUID id) {
        return jdbc.sql("select status from food_lots where id = ?").param(id).query(String.class).single();
    }
}
