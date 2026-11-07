package com.example.foodrescue.common;

import com.example.foodrescue.AuditTestSupport;
import com.example.foodrescue.IntegrationTest;
import com.example.foodrescue.lot.LotService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
@WithMockUser(username = "donor1", roles = "DONOR")
class AuditIntegrationTest {

    private static final String CREATE_LOT = "CREATE_LOT";
    private static final String UPDATE_LOT = "UPDATE_LOT";
    private static final String TITLE = "Хліб дня";

    @Autowired
    LotService lotService;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Test
    void create_ShouldWriteSuccessRecord() {
        lotService.create(AuditTestSupport.lotRequest(TITLE));

        List<Map<String, Object>> rows = AuditTestSupport.auditRows(jdbc, CREATE_LOT);
        assertThat(rows).hasSize(1);
        Map<String, Object> row = rows.getFirst();
        assertThat(row.get("username")).isEqualTo("donor1");
        assertThat(row.get("success")).isEqualTo(true);
        assertThat((String) row.get("method_name")).contains("create");
        assertThat((String) row.get("arguments")).contains(TITLE);
        assertThat((String) row.get("result")).contains(TITLE);
        assertThat(row.get("error_message")).isNull();
        assertThat(row.get("created_at")).isNotNull();
    }

    @Test
    void businessRuleViolation_ShouldWriteFailedRecordAndRethrow() {
        UUID id = lotService.create(AuditTestSupport.lotRequest(TITLE)).id();
        lotService.publish(id);

        assertThatThrownBy(() -> lotService.update(id, AuditTestSupport.lotRequest("Інша назва")))
                .isInstanceOf(ConflictException.class);

        List<Map<String, Object>> rows = AuditTestSupport.auditRows(jdbc, UPDATE_LOT);
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().get("success")).isEqualTo(false);
        assertThat((String) rows.getFirst().get("error_message")).contains("LotNotDraftException");
        assertThat(jdbc.sql("select title from food_lots where id = ?").param(id).query(String.class).single())
                .isEqualTo(TITLE);
    }

    @Test
    void outerTransactionRollback_ShouldKeepAuditRecord() {
        long before = countLots();

        transactionTemplate.executeWithoutResult(status -> {
            lotService.create(AuditTestSupport.lotRequest(TITLE));
            status.setRollbackOnly();
        });

        assertThat(countLots()).isEqualTo(before);
        assertThat(AuditTestSupport.auditRows(jdbc, CREATE_LOT)).hasSize(1);
    }

    private long countLots() {
        return jdbc.sql("select count(*) from food_lots").query(Long.class).single();
    }
}
