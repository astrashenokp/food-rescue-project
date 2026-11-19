package com.example.foodrescue.delivery;

import com.example.foodrescue.AuditTestSupport;
import com.example.foodrescue.IntegrationTest;
import com.example.foodrescue.common.FoodCategory;
import com.example.foodrescue.common.InvalidLotStateException;
import com.example.foodrescue.common.LotStatus;
import com.example.foodrescue.delivery.internal.exception.InvalidConfirmationCodeException;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
class DeliveryAuditIntegrationTest {

    private static final String CREATE_DESTINATION_POINT = "CREATE_DESTINATION_POINT";
    private static final String PICKUP_LOT = "PICKUP_LOT";
    private static final String DELIVER_LOT = "DELIVER_LOT";
    private static final String CONFIRM_DELIVERY = "CONFIRM_DELIVERY";
    private static final String VOLUNTEER = "volunteer1";
    private static final String COORDINATOR = "coordinator1";

    @Autowired DeliveryService deliveryService;
    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired UserDetailsService users;

    @Test
    @WithMockUser(username = COORDINATOR, roles = "COORDINATOR")
    void createDestinationPoint_ShouldWriteSuccessRecord() {
        String name = "Пункт " + UUID.randomUUID();
        long pointsBefore = countPoints();

        DestinationPointResponse point = deliveryService.createDestinationPoint(pointRequest(name));

        Map<String, Object> row = singleRow(CREATE_DESTINATION_POINT);
        assertSucceeded(row, COORDINATOR, "createDestinationPoint");
        assertThat((String) row.get("arguments")).contains(name);
        assertThat((String) row.get("result")).contains(name);
        assertThat(countPoints()).isEqualTo(pointsBefore + 1);
        assertThat(jdbc.sql("select name from destination_points where id = :id")
                .param("id", point.id()).query(String.class).single()).isEqualTo(name);
    }

    @Test
    @WithUserDetails(VOLUNTEER)
    void pickup_ShouldWriteSuccessRecord() throws Exception {
        UUID lotId = fixtures().reserveLot();
        restoreTestAuthentication();

        DeliveryResponse result = deliveryService.pickup(lotId, new PickupRequest(new BigDecimal("5.0")));

        Map<String, Object> row = singleRow(PICKUP_LOT);
        assertSucceeded(row, VOLUNTEER, "pickup");
        assertThat((String) row.get("arguments")).contains(lotId.toString());
        assertThat((String) row.get("result")).contains("lotStatus=PICKED_UP");
        assertThat(result.lotStatus()).isEqualTo(LotStatus.PICKED_UP);
        assertThat(statusOf(lotId)).isEqualTo("PICKED_UP");
        assertThat(countDeliveries(lotId)).isEqualTo(1);
    }

    @Test
    @WithUserDetails(VOLUNTEER)
    void deliver_ShouldWriteSuccessRecordWithoutConfirmationCode() throws Exception {
        UUID lotId = fixtures().reserveLot();
        restoreTestAuthentication();
        deliveryService.pickup(lotId, new PickupRequest(new BigDecimal("5")));
        UUID pointId = fixtures().createPoint("BAKERY");
        restoreTestAuthentication();

        DeliveryResponse result = deliveryService.deliver(lotId, new DeliveryRequest(pointId));

        Map<String, Object> row = singleRow(DELIVER_LOT);
        assertSucceeded(row, VOLUNTEER, "deliver");
        assertThat((String) row.get("arguments")).contains(lotId.toString()).contains(pointId.toString());
        assertThat((String) row.get("result")).contains("lotStatus=DELIVERED", "confirmationCode=[REDACTED]")
                .doesNotContain("confirmationCode=" + result.confirmationCode());
        assertThat(result.confirmationCode()).matches("\\d{6}");
        assertThat(statusOf(lotId)).isEqualTo("DELIVERED");
        assertThat(destinationOf(lotId)).isEqualTo(pointId);
    }

    @Test
    @WithUserDetails(VOLUNTEER)
    void confirm_ShouldWriteSuccessRecordAndHideCodeInArgumentsAndResult() throws Exception {
        UUID lotId = fixtures().createDeliveredLot();
        String code = fixtures().confirmationCode(lotId);
        restoreTestAuthentication();

        DeliveryResponse result = deliveryService.confirm(lotId,
                new ConfirmationRequest(code, new BigDecimal("5")));

        Map<String, Object> row = singleRow(CONFIRM_DELIVERY);
        assertSucceeded(row, VOLUNTEER, "confirm");
        assertThat(row.get("arguments")).isNull();
        assertThat((String) row.get("result")).contains("lotStatus=CONFIRMED", "confirmationCode=[REDACTED]")
                .doesNotContain("confirmationCode=" + code);
        assertThat(result.lotStatus()).isEqualTo(LotStatus.CONFIRMED);
        assertThat(statusOf(lotId)).isEqualTo("CONFIRMED");

        Map<String, Object> deliveryRow = singleRow(DELIVER_LOT);
        assertThat((String) deliveryRow.get("result"))
                .contains("confirmationCode=[REDACTED]")
                .doesNotContain("confirmationCode=" + code);
    }

    @Test
    @WithUserDetails(VOLUNTEER)
    void pickup_Repeated_ShouldAuditInvalidStateAndPreserveDelivery() throws Exception {
        UUID lotId = fixtures().reserveLot();
        restoreTestAuthentication();
        PickupRequest request = new PickupRequest(new BigDecimal("5"));
        deliveryService.pickup(lotId, request);

        assertThatThrownBy(() -> deliveryService.pickup(lotId, request))
                .isInstanceOf(InvalidLotStateException.class);

        List<Map<String, Object>> rows = auditRows(PICKUP_LOT);
        assertThat(rows).hasSize(2);
        assertSucceeded(rows.getFirst(), VOLUNTEER, "pickup");
        assertFailed(rows.getLast(), VOLUNTEER, "InvalidLotStateException");
        assertThat(statusOf(lotId)).isEqualTo("PICKED_UP");
        assertThat(countDeliveries(lotId)).isEqualTo(1);
    }

    @Test
    @WithUserDetails(VOLUNTEER)
    void deliver_Repeated_ShouldAuditInvalidStateAndKeepDestination() throws Exception {
        UUID lotId = fixtures().createDeliveredLot();
        UUID originalPoint = destinationOf(lotId);
        UUID nextPoint = fixtures().createPoint("BAKERY");
        restoreTestAuthentication();

        assertThatThrownBy(() -> deliveryService.deliver(lotId, new DeliveryRequest(nextPoint)))
                .isInstanceOf(InvalidLotStateException.class);

        List<Map<String, Object>> rows = auditRows(DELIVER_LOT);
        assertThat(rows).hasSize(2);
        assertSucceeded(rows.getFirst(), VOLUNTEER, "deliver");
        assertFailed(rows.getLast(), VOLUNTEER, "InvalidLotStateException");
        assertThat(statusOf(lotId)).isEqualTo("DELIVERED");
        assertThat(destinationOf(lotId)).isEqualTo(originalPoint);
    }

    @Test
    @WithUserDetails(VOLUNTEER)
    void confirm_WrongCode_ShouldAuditFailureWithoutLeakingCode() throws Exception {
        UUID lotId = fixtures().createDeliveredLot();
        String validCode = fixtures().confirmationCode(lotId);
        String invalidCode = validCode.equals("000000") ? "999999" : "000000";
        int attemptsBefore = failedAttempts(lotId);
        restoreTestAuthentication();

        assertThatThrownBy(() -> deliveryService.confirm(lotId,
                new ConfirmationRequest(invalidCode, new BigDecimal("5"))))
                .isInstanceOf(InvalidConfirmationCodeException.class);

        Map<String, Object> row = singleRow(CONFIRM_DELIVERY);
        assertFailed(row, VOLUNTEER, "InvalidConfirmationCodeException");
        assertThat(row.get("arguments")).isNull();
        assertThat((String) row.get("error_message")).doesNotContain(invalidCode, validCode);
        assertThat(statusOf(lotId)).isEqualTo("DELIVERED");
        assertThat(failedAttempts(lotId)).isEqualTo(attemptsBefore + 1);
    }

    @Test
    @WithMockUser(username = COORDINATOR, roles = "COORDINATOR")
    void createDestinationPoint_InvalidRequest_ShouldAuditValidationFailureAndKeepTable() {
        long pointsBefore = countPoints();

        assertThatThrownBy(() -> deliveryService.createDestinationPoint(pointRequest("x")))
                .isInstanceOf(ConstraintViolationException.class);

        assertFailed(singleRow(CREATE_DESTINATION_POINT), COORDINATOR, "ConstraintViolationException");
        assertThat(countPoints()).isEqualTo(pointsBefore);
    }

    @Test
    @WithMockUser(username = "donor1", roles = "DONOR")
    void createDestinationPoint_DonorRole_ShouldAuditDenialAndKeepTable() {
        long pointsBefore = countPoints();

        assertThatThrownBy(() -> deliveryService.createDestinationPoint(pointRequest("Пункт для доставки")))
                .isInstanceOf(AuthorizationDeniedException.class);

        assertFailed(singleRow(CREATE_DESTINATION_POINT), "donor1", "AuthorizationDeniedException");
        assertThat(countPoints()).isEqualTo(pointsBefore);
    }

    @Test
    @WithMockUser(username = "donor1", roles = "DONOR")
    void pickup_DonorRole_ShouldAuditDenialAndKeepReservedLot() throws Exception {
        UUID lotId = fixtures().reserveLot();
        long deliveriesBefore = countDeliveries(lotId);
        restoreTestAuthentication();

        assertThatThrownBy(() -> deliveryService.pickup(lotId, new PickupRequest(new BigDecimal("5"))))
                .isInstanceOf(AuthorizationDeniedException.class);

        assertFailed(singleRow(PICKUP_LOT), "donor1", "AuthorizationDeniedException");
        assertThat(statusOf(lotId)).isEqualTo("RESERVED");
        assertThat(countDeliveries(lotId)).isEqualTo(deliveriesBefore);
    }

    private void restoreTestAuthentication() {
        SecurityContextHolder.setContext(TestSecurityContextHolder.getContext());
    }

    private DeliveryTestFixtures fixtures() {
        return new DeliveryTestFixtures(mvc, jdbc, users);
    }

    private DestinationPointRequest pointRequest(String name) {
        return new DestinationPointRequest(UUID.randomUUID(), name, "Київ, вул. Доставки, 2",
                "09:00-18:00", Set.of(FoodCategory.BAKERY));
    }

    private long countPoints() {
        return jdbc.sql("select count(*) from destination_points").query(Long.class).single();
    }

    private long countDeliveries(UUID lotId) {
        return jdbc.sql("select count(*) from deliveries where lot_id = :lotId")
                .param("lotId", lotId).query(Long.class).single();
    }

    private int failedAttempts(UUID lotId) {
        return jdbc.sql("select failed_confirmation_attempts from deliveries where lot_id = :lotId")
                .param("lotId", lotId).query(Integer.class).single();
    }

    private UUID destinationOf(UUID lotId) {
        return jdbc.sql("select destination_point_id from deliveries where lot_id = :lotId")
                .param("lotId", lotId).query(UUID.class).single();
    }

    private String statusOf(UUID lotId) {
        return jdbc.sql("select status from food_lots where id = :lotId")
                .param("lotId", lotId).query(String.class).single();
    }

    private List<Map<String, Object>> auditRows(String action) {
        return AuditTestSupport.auditRows(jdbc, action);
    }

    private Map<String, Object> singleRow(String action) {
        List<Map<String, Object>> rows = auditRows(action);
        assertThat(rows).hasSize(1);
        return rows.getFirst();
    }

    private void assertSucceeded(Map<String, Object> row, String username, String methodName) {
        assertThat(row.get("username")).isEqualTo(username);
        assertThat(row.get("success")).isEqualTo(true);
        assertThat(row.get("error_message")).isNull();
        assertThat((String) row.get("method_name")).contains("DeliveryService", methodName);
        assertThat(row.get("created_at")).isNotNull();
        assertThat(((Number) row.get("duration_ms")).longValue()).isGreaterThanOrEqualTo(0L);
    }

    private void assertFailed(Map<String, Object> row, String username, String exceptionName) {
        assertThat(row.get("username")).isEqualTo(username);
        assertThat(row.get("success")).isEqualTo(false);
        assertThat((String) row.get("error_message")).contains(exceptionName);
        assertThat(row.get("created_at")).isNotNull();
    }
}
