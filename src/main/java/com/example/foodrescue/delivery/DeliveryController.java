package com.example.foodrescue.delivery;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
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
        DestinationPointResponse point = deliveryService.createDestinationPoint(request);
        return ResponseEntity
                .created(URI.create("/api/v1/destination-points/" + point.id()))
                .build();
    }

    @GetMapping("/destination-points")
    public List<DestinationPointResponse> getDestinationPoints() {
        return deliveryService.getDestinationPoints();
    }

    @GetMapping("/destination-points/{id}")
    public DestinationPointResponse getDestinationPoint(@PathVariable UUID id) {
        return deliveryService.getDestinationPoint(id);
    }

    @PutMapping("/destination-points/{id}")
    public DestinationPointResponse updateDestinationPoint(
            @PathVariable UUID id,
            @Valid @RequestBody DestinationPointRequest request) {
        return deliveryService.updateDestinationPoint(id, request);
    }

    @DeleteMapping("/destination-points/{id}")
    public ResponseEntity<Void> deleteDestinationPoint(@PathVariable UUID id) {
        deliveryService.deleteDestinationPoint(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/deliveries")
    public List<DeliveryResponse> getDeliveries() {
        return deliveryService.getDeliveries();
    }

    @GetMapping("/deliveries/{id}")
    public DeliveryResponse getDelivery(@PathVariable UUID id) {
        return deliveryService.getDelivery(id);
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
