package com.example.foodrescue.delivery;

import java.util.List;
import java.util.UUID;

public interface DeliveryService {

    DestinationPoint createDestinationPoint(DestinationPointRequest request);

    DestinationPoint getDestinationPoint(UUID id);

    DeliveryResponse pickup(UUID lotId, PickupRequest request);

    DeliveryResponse deliver(UUID lotId, DeliveryRequest request);

    DeliveryResponse confirm(UUID lotId, ConfirmationRequest request);

    List<StatusHistoryResponse> getHistory(UUID lotId);
}
