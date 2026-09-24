package com.example.foodrescue.delivery;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class DeliveryController {

    private final DeliveryService deliveryService;

    public DeliveryController(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @PostMapping("/destination-points")
    public ResponseEntity<Void> createDestinationPoint(@Valid @RequestBody DestinationPointRequest request) {
        DestinationPoint point = deliveryService.createDestinationPoint(request);
        return ResponseEntity
                .created(URI.create("/api/v1/destination-points/" + point.getId()))
                .build();
    }

    @GetMapping("/destination-points/{id}")
    public DestinationPointResponse getDestinationPoint(@PathVariable UUID id) {
        DestinationPoint point = deliveryService.getDestinationPoint(id);
        return new DestinationPointResponse(
                point.getId(),
                point.getOrganizationId(),
                point.getName(),
                point.getAddress(),
                point.getWorkingHours(),
                Set.copyOf(point.getAcceptedCategories()));
    }

    @PostMapping("/lots/{id}/pickup")
    public DeliveryResponse pickup(@PathVariable UUID id, @Valid @RequestBody PickupRequest request) {
        return deliveryService.pickup(id, request);
    }

    @PostMapping("/lots/{id}/delivery")
    public DeliveryResponse deliver(@PathVariable UUID id, @Valid @RequestBody DeliveryRequest request) {
        return deliveryService.deliver(id, request);
    }

    @PostMapping("/lots/{id}/confirmation")
    public DeliveryResponse confirm(@PathVariable UUID id, @Valid @RequestBody ConfirmationRequest request) {
        return deliveryService.confirm(id, request);
    }

    @GetMapping("/lots/{id}/history")
    public List<StatusHistoryResponse> getHistory(@PathVariable UUID id) {
        return deliveryService.getHistory(id);
    }
}
