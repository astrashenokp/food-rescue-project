package com.example.foodrescue.delivery;

import com.example.foodrescue.common.LotResponse;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;
import java.util.UUID;

public interface DeliveryService {

    @PreAuthorize("hasRole('COORDINATOR')")
    DestinationPointResponse createDestinationPoint(DestinationPointRequest request);

    List<DestinationPointResponse> getDestinationPoints();

    DestinationPointResponse getDestinationPoint(UUID id);

    @PreAuthorize("hasRole('COORDINATOR')")
    DestinationPointResponse updateDestinationPoint(UUID id, DestinationPointRequest request);

    @PreAuthorize("hasRole('COORDINATOR')")
    void deleteDestinationPoint(UUID id);

    List<LotResponse> getReservedLots();

    LotResponse getLotOverview(UUID lotId);

    @PreAuthorize("hasAnyRole('VOLUNTEER', 'COORDINATOR')")
    List<DeliveryResponse> getDeliveries();

    @PreAuthorize("hasAnyRole('VOLUNTEER', 'COORDINATOR')")
    DeliveryResponse getDelivery(UUID lotId);

    @PreAuthorize("hasRole('VOLUNTEER')")
    DeliveryResponse pickup(UUID lotId, PickupRequest request);

    @PreAuthorize("hasRole('VOLUNTEER')")
    DeliveryResponse deliver(UUID lotId, DeliveryRequest request);

    @PreAuthorize("hasRole('VOLUNTEER')")
    DeliveryResponse confirm(UUID lotId, ConfirmationRequest request);

    List<StatusHistoryResponse> getHistory(UUID lotId);
}
